package com.example.editor.portability.merge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.example.editor.config.StorageProperties;
import com.example.editor.portability.CapabilityPackageChecker;
import com.example.editor.portability.CapabilitySelection;
import com.example.editor.portability.HydraCapabilityKind;
import com.example.editor.portability.HydraCapabilityPackage;
import com.example.editor.portability.HydraCapabilityPackageManifest;
import com.example.editor.portability.MetamodelOrigin;
import com.example.editor.portability.MetamodelOriginKind;
import com.example.editor.portability.transaction.CapabilityLayerRollbackRequest;
import com.example.editor.portability.transaction.CapabilityLayerRollbackResult;
import com.example.editor.portability.transaction.CapabilityLayerTransactionProperties;
import com.example.editor.portability.transaction.CapabilityLayerTransactionResult;
import com.example.editor.portability.transaction.CapabilityLayerTransactionService;
import com.example.editor.project.storage.CoreProjectWorkspaceContributor;
import com.example.editor.project.storage.ProjectArtifactClassifier;
import com.example.editor.project.storage.ProjectWorkspaceContributionRegistry;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Fusion de capas de capacidades con decisiones por conflicto, aplicacion real sobre disco y reversion.
 *
 * <p>Etapas certificadas: {@code portability_package_merge.a2}, {@code .a3}, {@code .a4} y
 * {@code x_package_and_move_capabilities.m1}. Relacionadas sin demostrar: {@code x_package_and_move_capabilities.p1},
 * {@code portability_package_merge.d1}, {@code portability_package_merge.d2}.</p>
 *
 * <p>Colaboradores reales: servicio de fusion semantica, servicio de transacciones de capas, comprobador de
 * paquetes y registro de repositorios de trabajo con sus carpetas en un {@code @TempDir}. El oraculo lee los ficheros
 * del disco, descodifica el diario y recalcula los SHA-256 por su cuenta.</p>
 */
class CapabilitySemanticMergeDecisionApplyRollbackIntegrationTests {
    private static final String META = "metamodels/library.metamodel.json";
    private static final String VALS = "behavior/validations/library.validations.json";

    private static final String EXISTING_META = "{\"id\":\"library\",\"name\":\"Library\",\"description\":\"Existing description\","
            + "\"classes\":[{\"id\":\"class:Book\",\"name\":\"Book\",\"abstract\":false},{\"id\":\"class:Author\",\"name\":\"Author\"}]}";
    private static final String RECOVERED_META = "{\"id\":\"library\",\"name\":\"Library\",\"description\":\"Recovered description\","
            + "\"classes\":[{\"id\":\"class:Book\",\"name\":\"Book\",\"abstract\":true},{\"id\":\"class:Magazine\",\"name\":\"Magazine\"}]}";
    private static final String RECOVERED_VALS = "{\"id\":\"validations:library\",\"rules\":["
            + "{\"id\":\"rule:IsbnRequired\",\"name\":\"IsbnRequired\",\"dependsOn\":\"rule:IsbnFormat\"},"
            + "{\"id\":\"rule:IsbnFormat\",\"name\":\"IsbnFormat\"}]}";
    private static final String RECOVERED_VALS_DANGLING = "{\"id\":\"validations:library\",\"rules\":["
            + "{\"id\":\"rule:IsbnRequired\",\"name\":\"IsbnRequired\",\"dependsOn\":\"rule:DoesNotExist\"}]}";

    @TempDir Path temporary;

    private final ObjectMapper mapper = JsonMapper.builder().findAndAddModules().build();
    private Path metamodelsDirectory;
    private Path validationsDirectory;
    private Path transactionsDirectory;
    private Path metaFile;
    private Path valsFile;
    private byte[] existingBytes;
    private CapabilitySemanticMergeService service;

    @BeforeEach
    void setUp() throws Exception {
        Path workspace = temporary.resolve("workspace");
        metamodelsDirectory = workspace.resolve("metamodels");
        validationsDirectory = workspace.resolve("validations");
        transactionsDirectory = temporary.resolve("transactions");
        metaFile = metamodelsDirectory.resolve("library.metamodel.json");
        valsFile = validationsDirectory.resolve("library.validations.json");
        Files.createDirectories(metamodelsDirectory);
        existingBytes = EXISTING_META.getBytes(StandardCharsets.UTF_8);
        Files.write(metaFile, existingBytes);

        StorageProperties storage = new StorageProperties(metamodelsDirectory, workspace.resolve("models"),
                workspace.resolve("notations"), validationsDirectory, workspace.resolve("quickfixes"),
                workspace.resolve("generators"), workspace.resolve("assets"), workspace.resolve("exports"), true);
        ProjectWorkspaceContributionRegistry registry = new ProjectWorkspaceContributionRegistry(
                List.of(new CoreProjectWorkspaceContributor(storage)));
        CapabilityLayerTransactionProperties properties = new CapabilityLayerTransactionProperties();
        properties.setStorageRoot(transactionsDirectory.toString());
        CapabilityLayerTransactionService transactions = new CapabilityLayerTransactionService(properties,
                new CapabilityPackageChecker(), registry, new ProjectArtifactClassifier(), mapper);
        service = new CapabilitySemanticMergeService(transactions, mapper);
    }

    @Test
    @DisplayName("a2, a3, m1: la previsualizacion detecta los choques y propone la fusion sin tocar el proyecto")
    void previewDetectsConflictsAndProposesTheMergeWithoutTouchingTheProject() throws Exception {
        CapabilitySemanticMergeRequest request = request(RECOVERED_VALS, Map.of(), Map.of(META, etag(existingBytes)), false);

        CapabilitySemanticMergePreview preview = service.preview(request);

        assertThat(preview.applicable()).isTrue();
        assertThat(preview.previewId()).startsWith("semantic-merge-");
        Map<String, String> conflictTypes = new LinkedHashMap<>();
        preview.conflicts().forEach(conflict -> conflictTypes.put(conflict.key(), conflict.type()));
        assertThat(conflictTypes).containsOnly(
                Map.entry(META + "#document", "DOCUMENT"),
                Map.entry(META + "/description", "VALUE"),
                Map.entry(META + "/classes/class:Book", "ENTITY"),
                Map.entry(META + "/classes/class:Book/abstract", "VALUE"));
        assertThat(preview.items()).extracting(CapabilitySemanticMergeItem::key).contains(
                META + "/classes/class:Author", META + "/classes/class:Magazine", VALS + "#document");
        assertThat(preview.dependencies()).hasSize(1);
        assertThat(preview.dependencies().get(0).targetReference()).isEqualTo("rule:IsbnFormat");
        assertThat(preview.dependencies().get(0).resolved()).isTrue();
        // Sin decisiones explicitas gana lo existente en los escalares y se unen las entidades.
        JsonNode mergedMeta = preview.mergedPackage().canonicalDocuments().get(META);
        assertThat(mergedMeta.get("description").asString()).isEqualTo("Existing description");
        assertThat(classIds(mergedMeta)).containsExactly("class:Author", "class:Book", "class:Magazine");
        assertThat(classById(mergedMeta, "class:Book").get("abstract").asBoolean()).isFalse();

        // La aplicacion sin persistir tampoco cambia nada y propone REPLACE para lo existente y CREATE para lo nuevo.
        CapabilitySemanticMergeResult dryRun = service.apply(request);
        assertThat(dryRun.transaction().persisted()).isFalse();
        assertThat(dryRun.transaction().preview().applicable()).isTrue();
        Map<String, String> actions = new LinkedHashMap<>();
        dryRun.transaction().preview().items().forEach(item -> actions.put(item.documentPath(), item.action()));
        assertThat(actions).containsOnly(Map.entry(META, "REPLACE"), Map.entry(VALS, "CREATE"));
        assertThat(Files.readAllBytes(metaFile)).isEqualTo(existingBytes);
        assertThat(valsFile).doesNotExist();
        assertThat(transactionsDirectory.resolve("journals")).doesNotExist();
    }

    @Test
    @DisplayName("a4: cada decision (conservar, usar lo recuperado, fusionar, omitir) gobierna el documento resultante")
    void eachDecisionGovernsTheResultingDocument() throws Exception {
        // Conservar / usar lo recuperado / omitir sobre entidades y escalares.
        Map<String, CapabilitySemanticMergeDecision> decisions = new LinkedHashMap<>();
        decisions.put(META + "/description", CapabilitySemanticMergeDecision.USE_RECOVERED);
        decisions.put(META + "/classes/class:Book", CapabilitySemanticMergeDecision.KEEP_EXISTING);
        decisions.put(META + "/classes/class:Magazine", CapabilitySemanticMergeDecision.SKIP);
        decisions.put(META + "/classes/class:Author", CapabilitySemanticMergeDecision.USE_RECOVERED);
        CapabilitySemanticMergePreview preview = service.preview(request(RECOVERED_VALS, decisions, Map.of(), false));

        JsonNode merged = preview.mergedPackage().canonicalDocuments().get(META);
        assertThat(merged.get("description").asString()).isEqualTo("Recovered description");
        assertThat(merged.get("classes")).isEqualTo(mapper.readTree("[{\"id\":\"class:Book\",\"name\":\"Book\",\"abstract\":false}]"));
        assertThat(preview.keptElements()).isEqualTo(1);
        assertThat(preview.recoveredElements()).isEqualTo(2);
        assertThat(preview.mergedElements()).isEqualTo(2);
        assertThat(preview.skippedElements()).isEqualTo(1);
        assertThat(preview.items()).filteredOn(item -> item.key().equals(META + "/classes/class:Magazine"))
                .singleElement().satisfies(item -> assertThat(item.selectedDecision()).isEqualTo(CapabilitySemanticMergeDecision.SKIP));

        // Fusionar una entidad y quedarse con lo recuperado solo en una propiedad.
        Map<String, CapabilitySemanticMergeDecision> deep = new LinkedHashMap<>();
        deep.put(META + "/classes/class:Book", CapabilitySemanticMergeDecision.MERGE);
        deep.put(META + "/classes/class:Book/abstract", CapabilitySemanticMergeDecision.USE_RECOVERED);
        JsonNode deepMerged = service.preview(request(RECOVERED_VALS, deep, Map.of(), false))
                .mergedPackage().canonicalDocuments().get(META);
        assertThat(classById(deepMerged, "class:Book")).isEqualTo(
                mapper.readTree("{\"id\":\"class:Book\",\"name\":\"Book\",\"abstract\":true}"));
        assertThat(classIds(deepMerged)).containsExactly("class:Author", "class:Book", "class:Magazine");

        // Decisiones a nivel de documento completo.
        CapabilitySemanticMergePreview recovered = service.preview(request(RECOVERED_VALS,
                Map.of(META + "#document", CapabilitySemanticMergeDecision.USE_RECOVERED), Map.of(), false));
        assertThat(recovered.mergedPackage().canonicalDocuments().get(META)).isEqualTo(mapper.readTree(RECOVERED_META));
        CapabilitySemanticMergePreview kept = service.preview(request(RECOVERED_VALS,
                Map.of(META + "#document", CapabilitySemanticMergeDecision.KEEP_EXISTING), Map.of(), false));
        assertThat(kept.mergedPackage().canonicalDocuments().get(META)).isEqualTo(mapper.readTree(EXISTING_META));
        CapabilitySemanticMergePreview skipped = service.preview(request(RECOVERED_VALS,
                Map.of(META + "#document", CapabilitySemanticMergeDecision.SKIP), Map.of(), false));
        assertThat(skipped.mergedPackage().canonicalDocuments()).doesNotContainKey(META).containsKey(VALS);
    }

    @Test
    @DisplayName("a3, m1: una referencia sin resolver bloquea la fusion y no se escribe nada")
    void anUnresolvedReferenceBlocksTheMergeAndNothingIsWritten() throws Exception {
        CapabilitySemanticMergeRequest request = request(RECOVERED_VALS_DANGLING, Map.of(), Map.of(META, etag(existingBytes)), true);

        CapabilitySemanticMergePreview preview = service.preview(request);

        assertThat(preview.applicable()).isFalse();
        assertThat(preview.dependencies()).filteredOn(dependency -> !dependency.resolved())
                .extracting(CapabilitySemanticMergeDependency::targetReference).containsExactly("rule:DoesNotExist");
        assertThat(preview.diagnostics()).contains("1 unresolved cross-layer reference(s) block the merge");
        assertThatThrownBy(() -> service.apply(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("blocking dependencies or conflicts");
        assertThat(Files.readAllBytes(metaFile)).isEqualTo(existingBytes);
        assertThat(valsFile).doesNotExist();
        assertThat(transactionsDirectory.resolve("journals")).doesNotExist();
    }

    @Test
    @DisplayName("m1, a4: aplicar con decisiones deja las capas en disco con su marca de version y un diario de reversion")
    void applyingWithDecisionsWritesTheLayersWithVersionMarksAndAJournal() throws Exception {
        CapabilityLayerTransactionResult transaction = applyWithDecisions();

        assertThat(transaction.persisted()).isTrue();
        assertThat(transaction.rollbackAvailable()).isTrue();
        assertThat(transaction.transactionId()).startsWith("cap-tx-");
        // Contenido en disco (oraculo: relectura de los ficheros).
        assertThat(mapper.readTree(Files.readString(metaFile))).isEqualTo(mapper.readTree(
                "{\"id\":\"library\",\"name\":\"Library\",\"description\":\"Recovered description\",\"classes\":["
                        + "{\"id\":\"class:Author\",\"name\":\"Author\"},"
                        + "{\"id\":\"class:Book\",\"name\":\"Book\",\"abstract\":false},"
                        + "{\"id\":\"class:Magazine\",\"name\":\"Magazine\"}]}"));
        assertThat(mapper.readTree(Files.readString(valsFile))).isEqualTo(mapper.readTree(RECOVERED_VALS));
        // Marca de version de cada documento = SHA-256 de sus bytes en disco, calculado aqui.
        assertThat(transaction.appliedEtags()).containsOnly(
                Map.entry(META, etag(Files.readAllBytes(metaFile))),
                Map.entry(VALS, etag(Files.readAllBytes(valsFile))));
        // El diario guarda los bytes originales de lo que existia y marca lo creado.
        JsonNode journal = mapper.readTree(Files.readString(
                transactionsDirectory.resolve("journals").resolve(transaction.transactionId() + ".json")));
        assertThat(journal.get("items").size()).isEqualTo(2);
        for (JsonNode item : journal.get("items")) {
            String document = item.get("documentPath").asString();
            if (META.equals(document)) {
                assertThat(item.get("existed").asBoolean()).isTrue();
                assertThat(Base64.getDecoder().decode(item.get("originalBase64").asString())).isEqualTo(existingBytes);
            } else {
                assertThat(document).isEqualTo(VALS);
                assertThat(item.get("existed").asBoolean()).isFalse();
            }
        }
        // El area de preparacion queda limpia.
        assertThat(transactionsDirectory.resolve("staging").resolve(transaction.transactionId())).doesNotExist();
    }

    @Test
    @DisplayName("p1 (relacionada): una version obsoleta o ausente se rechaza sin escritura parcial")
    void aStaleOrMissingVersionIsRejectedWithoutPartialWrites() throws Exception {
        String loadedEtag = etag(existingBytes);
        byte[] changedByAnotherPerson = "{\"id\":\"library\",\"description\":\"changed meanwhile\"}".getBytes(StandardCharsets.UTF_8);
        Files.write(metaFile, changedByAnotherPerson);

        CapabilitySemanticMergeRequest stale = request(RECOVERED_VALS, Map.of(), Map.of(META, loadedEtag), true);
        assertThatThrownBy(() -> service.apply(stale))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("preconditions failed");
        assertThat(service.apply(request(RECOVERED_VALS, Map.of(), Map.of(META, loadedEtag), false))
                .transaction().preview().items()).filteredOn(item -> item.documentPath().equals(META))
                .singleElement().satisfies(item -> assertThat(item.diagnostics()).contains("Layer changed since it was loaded"));

        CapabilitySemanticMergeRequest missing = request(RECOVERED_VALS, Map.of(), Map.of(), true);
        assertThatThrownBy(() -> service.apply(missing))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("preconditions failed");

        // Ningun documento se escribio, ni siquiera el libre, y no hay diario.
        assertThat(Files.readAllBytes(metaFile)).isEqualTo(changedByAnotherPerson);
        assertThat(valsFile).doesNotExist();
        assertThat(transactionsDirectory.resolve("journals")).doesNotExist();
    }

    @Test
    @DisplayName("d2 (relacionada): la reversion devuelve los bytes originales y elimina la capa creada")
    void rollbackRestoresTheOriginalBytesAndRemovesTheCreatedLayer() throws Exception {
        CapabilityLayerTransactionResult transaction = applyWithDecisions();
        assertThat(valsFile).isRegularFile();

        CapabilityLayerRollbackResult rollback = service.rollback(new CapabilityLayerRollbackRequest(
                transaction.transactionId(), List.of(), transaction.appliedEtags()));

        assertThat(rollback.restoredDocuments()).containsExactlyInAnyOrder(META, VALS);
        assertThat(rollback.skippedDocuments()).isEmpty();
        assertThat(rollback.journalClosed()).isTrue();
        assertThat(Files.readAllBytes(metaFile)).isEqualTo(existingBytes);
        assertThat(valsFile).doesNotExist();
        assertThat(transactionsDirectory.resolve("journals").resolve(transaction.transactionId() + ".json")).doesNotExist();
        try (Stream<Path> files = Files.list(metamodelsDirectory)) {
            assertThat(files.map(path -> path.getFileName().toString()).toList()).containsExactly("library.metamodel.json");
        }
    }

    @Test
    @DisplayName("d2 (relacionada): la reversion no pisa una capa cambiada despues de aplicar y deja el diario abierto")
    void rollbackSkipsALayerChangedAfterTheApplyAndKeepsTheJournalOpen() throws Exception {
        CapabilityLayerTransactionResult transaction = applyWithDecisions();
        byte[] laterEdit = "{\"id\":\"library\",\"description\":\"edited after apply\"}".getBytes(StandardCharsets.UTF_8);
        Files.write(metaFile, laterEdit);

        CapabilityLayerRollbackResult rollback = service.rollback(new CapabilityLayerRollbackRequest(
                transaction.transactionId(), List.of(), transaction.appliedEtags()));

        assertThat(rollback.restoredDocuments()).containsExactly(VALS);
        assertThat(rollback.skippedDocuments()).containsExactly(META);
        assertThat(rollback.journalClosed()).isFalse();
        assertThat(rollback.diagnostics())
                .contains("Rollback skipped because the layer changed after the transaction: " + META);
        assertThat(Files.readAllBytes(metaFile)).isEqualTo(laterEdit);
        assertThat(valsFile).doesNotExist();
        assertThat(transactionsDirectory.resolve("journals").resolve(transaction.transactionId() + ".json")).isRegularFile();
    }

    private CapabilityLayerTransactionResult applyWithDecisions() throws Exception {
        Map<String, CapabilitySemanticMergeDecision> decisions = new LinkedHashMap<>();
        decisions.put(META + "/description", CapabilitySemanticMergeDecision.USE_RECOVERED);
        decisions.put(META + "/classes/class:Book", CapabilitySemanticMergeDecision.KEEP_EXISTING);
        return service.apply(request(RECOVERED_VALS, decisions, Map.of(META, etag(existingBytes)), true)).transaction();
    }

    private CapabilitySemanticMergeRequest request(String recoveredValidations,
            Map<String, CapabilitySemanticMergeDecision> decisions, Map<String, String> expectedEtags, boolean persist) throws Exception {
        CapabilitySelection metamodel = new CapabilitySelection("metamodel", HydraCapabilityKind.METAMODEL, META, "2.1", true, false, Map.of());
        CapabilitySelection validations = new CapabilitySelection("validations", HydraCapabilityKind.VALIDATIONS, VALS, "1.0", true, false, Map.of());
        HydraCapabilityPackage existing = new HydraCapabilityPackage(manifest("existing", List.of(metamodel)),
                Map.of(META, mapper.readTree(EXISTING_META)), Map.of());
        Map<String, JsonNode> recoveredDocuments = new LinkedHashMap<>();
        recoveredDocuments.put(META, mapper.readTree(RECOVERED_META));
        recoveredDocuments.put(VALS, mapper.readTree(recoveredValidations));
        HydraCapabilityPackage recovered = new HydraCapabilityPackage(manifest("recovered", List.of(metamodel, validations)),
                recoveredDocuments, Map.of());
        return new CapabilitySemanticMergeRequest(existing, recovered, "library", expectedEtags, List.of(),
                CapabilitySemanticMergeDecision.MERGE, decisions, persist);
    }

    private static HydraCapabilityPackageManifest manifest(String id, List<CapabilitySelection> selections) {
        return new HydraCapabilityPackageManifest("1.0", "library-" + id, "Library " + id, "1.0.0", "library",
                new MetamodelOrigin(MetamodelOriginKind.OWNED, null, "", "", "", "", "", Map.of(), Map.of()),
                selections, List.of(), List.of(), Map.of(), Map.of());
    }

    private static List<String> classIds(JsonNode metamodel) {
        List<String> ids = new ArrayList<>();
        for (JsonNode item : metamodel.get("classes")) {
            ids.add(item.get("id").asString());
        }
        return ids;
    }

    private static JsonNode classById(JsonNode metamodel, String id) {
        for (JsonNode item : metamodel.get("classes")) {
            if (id.equals(item.get("id").asString())) {
                return item;
            }
        }
        throw new AssertionError("class not found: " + id);
    }

    private static String etag(byte[] content) throws Exception {
        return "\"" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content)) + "\"";
    }
}
