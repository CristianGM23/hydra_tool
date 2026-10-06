package com.example.editor.portability.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.example.editor.portability.CapabilityPackageChecker;
import com.example.editor.portability.CapabilitySelection;
import com.example.editor.portability.HydraCapabilityKind;
import com.example.editor.portability.HydraCapabilityPackage;
import com.example.editor.portability.HydraCapabilityPackageManifest;
import com.example.editor.portability.MetamodelOrigin;
import com.example.editor.portability.MetamodelOriginKind;
import com.example.editor.project.ArtifactKind;
import com.example.editor.project.storage.ProjectArtifactClassifier;
import com.example.editor.project.storage.ProjectWorkspaceContributionRegistry;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class CapabilityLayerTransactionServiceTests {
    @TempDir Path temporary;

    @Test
    void previewsAppliesAndRollsBackACompletePortableTransaction() throws Exception {
        Path metamodels = temporary.resolve("metamodels");
        CapabilityLayerTransactionService service = service(metamodels);
        HydraCapabilityPackage capabilityPackage = capabilityPackage("people", "people");
        CapabilityLayerTransactionRequest request = new CapabilityLayerTransactionRequest(capabilityPackage,
                "people", Map.of(), List.of("METAMODEL"), true);

        CapabilityLayerTransactionPreview preview = service.preview(request);
        CapabilityLayerTransactionResult result = service.apply(request);

        assertThat(preview.applicable()).isTrue();
        assertThat(preview.items()).singleElement().satisfies(item -> {
            assertThat(item.artifactKind()).isEqualTo(ArtifactKind.METAMODEL);
            assertThat(item.action()).isEqualTo("CREATE");
        });
        Path target = metamodels.resolve("people.metamodel.json");
        assertThat(result.persisted()).isTrue();
        assertThat(Files.readString(target)).contains("\"id\":\"people\"");

        CapabilityLayerRollbackResult rollback = service.rollback(new CapabilityLayerRollbackRequest(
                result.transactionId(), List.of(), result.appliedEtags()));

        assertThat(rollback.restoredDocuments()).containsExactly("metamodels/people.metamodel.json");
        assertThat(rollback.journalClosed()).isTrue();
        assertThat(target).doesNotExist();
    }

    @Test
    void blocksStaleEtagsAndPackagesForAnotherMetamodel() throws Exception {
        Path metamodels = temporary.resolve("metamodels");
        Files.createDirectories(metamodels);
        Files.writeString(metamodels.resolve("people.metamodel.json"), "{\"id\":\"old\"}");
        CapabilityLayerTransactionService service = service(metamodels);

        CapabilityLayerTransactionPreview stale = service.preview(new CapabilityLayerTransactionRequest(
                capabilityPackage("people", "people"), "people", Map.of("metamodels/people.metamodel.json", "\"stale\""),
                List.of(), false));
        CapabilityLayerTransactionPreview wrongTarget = service.preview(new CapabilityLayerTransactionRequest(
                capabilityPackage("orders", "orders"), "people", Map.of(), List.of(), false));
        HydraCapabilityPackage checksumPackage = capabilityPackage("checksum", "checksum");
        HydraCapabilityPackageManifest badManifest = new HydraCapabilityPackageManifest(checksumPackage.manifest().schemaVersion(),
                checksumPackage.manifest().id(), checksumPackage.manifest().name(), checksumPackage.manifest().packageVersion(),
                checksumPackage.manifest().metamodelId(), checksumPackage.manifest().metamodelOrigin(),
                checksumPackage.manifest().capabilities(), checksumPackage.manifest().dependencies(),
                checksumPackage.manifest().compatibility(), Map.of("metamodels/checksum.metamodel.json", "0".repeat(64)),
                checksumPackage.manifest().annotations());
        CapabilityLayerTransactionPreview checksumMismatch = service.preview(new CapabilityLayerTransactionRequest(
                new HydraCapabilityPackage(badManifest, checksumPackage.canonicalDocuments(), checksumPackage.resources()),
                "checksum", Map.of(), List.of(), false));

        assertThat(stale.applicable()).isFalse();
        assertThat(stale.items()).singleElement().satisfies(item -> assertThat(item.diagnostics())
                .contains("Layer changed since it was loaded"));
        assertThat(wrongTarget.applicable()).isFalse();
        assertThat(wrongTarget.diagnostics()).contains("Package metamodelId does not match targetMetamodelId");
        assertThat(checksumMismatch.applicable()).isFalse();
        assertThat(checksumMismatch.items()).singleElement().satisfies(item -> assertThat(item.diagnostics())
                .contains("Canonical document checksum does not match the package manifest"));
    }

    @Test
    void closesARecoveryJournalWhenThePlannedLayerWasNeverApplied() throws Exception {
        Path metamodels = temporary.resolve("metamodels");
        CapabilityLayerTransactionService service = service(metamodels);
        String transactionId = "cap-tx-interrupted";
        Path journalPath = temporary.resolve("transactions/journals").resolve(transactionId + ".json");
        Files.createDirectories(journalPath.getParent());
        ObjectMapper mapper = JsonMapper.builder().findAndAddModules().build();
        CapabilityLayerTransactionJournal journal = new CapabilityLayerTransactionJournal(transactionId, "people",
                java.time.Instant.parse("2026-08-04T00:00:00Z"), List.of(new CapabilityLayerTransactionJournalItem(
                        "metamodels/people.metamodel.json", metamodels.resolve("people.metamodel.json").toString(),
                        false, "", "\"" + "0".repeat(64) + "\"", false)));
        Files.write(journalPath, mapper.writeValueAsBytes(journal));

        CapabilityLayerRollbackResult rollback = service.rollback(new CapabilityLayerRollbackRequest(
                transactionId, List.of(), Map.of()));

        assertThat(rollback.restoredDocuments()).containsExactly("metamodels/people.metamodel.json");
        assertThat(rollback.journalClosed()).isTrue();
        assertThat(journalPath).doesNotExist();
    }

    @Test
    void rollsBackOnlySelectedLayersAndKeepsTheJournalOpen() throws Exception {
        Path metamodels = temporary.resolve("metamodels");
        CapabilityLayerTransactionService service = service(metamodels);
        HydraCapabilityPackage first = capabilityPackage("people", "people");
        ObjectMapper mapper = JsonMapper.builder().findAndAddModules().build();
        String secondPath = "metamodels/secondary.metamodel.json";
        Map<String, tools.jackson.databind.JsonNode> documents = new java.util.LinkedHashMap<>(first.canonicalDocuments());
        documents.put(secondPath, mapper.readTree("{\"schemaVersion\":\"2.1\",\"id\":\"secondary\",\"name\":\"secondary\",\"classes\":[],\"enums\":[],\"dataTypes\":[],\"relations\":[],\"annotations\":{}}"));
        List<CapabilitySelection> selections = new java.util.ArrayList<>(first.manifest().capabilities());
        selections.add(new CapabilitySelection("secondary", HydraCapabilityKind.METAMODEL, secondPath, "2.1", true, false, Map.of()));
        HydraCapabilityPackageManifest manifest = new HydraCapabilityPackageManifest("1.0", "people-layers", "people layers", "1.0.0", "people",
                first.manifest().metamodelOrigin(), selections, List.of(), List.of(), Map.of(), Map.of());
        CapabilityLayerTransactionResult result = service.apply(new CapabilityLayerTransactionRequest(
                new HydraCapabilityPackage(manifest, documents, Map.of()), "people", Map.of(), List.of("METAMODEL"), true));

        CapabilityLayerRollbackResult rollback = service.rollback(new CapabilityLayerRollbackRequest(result.transactionId(),
                List.of("metamodels/people.metamodel.json"), result.appliedEtags()));

        assertThat(rollback.restoredDocuments()).containsExactly("metamodels/people.metamodel.json");
        assertThat(rollback.journalClosed()).isFalse();
        assertThat(metamodels.resolve("people.metamodel.json")).doesNotExist();
        assertThat(metamodels.resolve("secondary.metamodel.json")).exists();
    }

    private CapabilityLayerTransactionService service(Path metamodels) {
        CapabilityLayerTransactionProperties properties = new CapabilityLayerTransactionProperties();
        properties.setStorageRoot(temporary.resolve("transactions").toString());
        ProjectWorkspaceContributionRegistry registry = new ProjectWorkspaceContributionRegistry(List.of()) {
            @Override public Snapshot snapshot(com.example.editor.project.ProjectLayout layout) {
                return new Snapshot(List.of(), Map.of(ArtifactKind.METAMODEL, metamodels));
            }
        };
        ObjectMapper mapper = JsonMapper.builder().findAndAddModules().build();
        return new CapabilityLayerTransactionService(properties, new CapabilityPackageChecker(), registry,
                new ProjectArtifactClassifier(), mapper);
    }

    private HydraCapabilityPackage capabilityPackage(String packageMetamodelId, String documentId) throws Exception {
        ObjectMapper mapper = JsonMapper.builder().findAndAddModules().build();
        String path = "metamodels/" + documentId + ".metamodel.json";
        HydraCapabilityPackageManifest manifest = new HydraCapabilityPackageManifest("1.0",
                packageMetamodelId + "-layers", packageMetamodelId + " layers", "1.0.0", packageMetamodelId,
                new MetamodelOrigin(MetamodelOriginKind.OWNED, null, "", "", "", "", "", Map.of(), Map.of()),
                List.of(new CapabilitySelection("metamodel", HydraCapabilityKind.METAMODEL, path, "2.1", true, false, Map.of())),
                List.of(), List.of(), Map.of(), Map.of());
        return new HydraCapabilityPackage(manifest, Map.of(path,
                mapper.readTree(("{\"schemaVersion\":\"2.1\",\"id\":\"" + documentId
                        + "\",\"name\":\"" + documentId + "\",\"classes\":[],\"enums\":[],\"dataTypes\":[],\"relations\":[],\"annotations\":{}}"))), Map.of());
    }
}
