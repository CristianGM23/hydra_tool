package com.example.editor.portability.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.editor.audit.AuditActions;
import com.example.editor.audit.AuditCanonicalDocumentMutationObserver;
import com.example.editor.audit.AuditCapturePolicy;
import com.example.editor.audit.AuditDiffStrategyRegistry;
import com.example.editor.audit.AuditLogProperties;
import com.example.editor.audit.AuditRedactionRegistry;
import com.example.editor.audit.AuditSnapshotService;
import com.example.editor.audit.DefaultAuditEventPublisher;
import com.example.editor.audit.GenericAuditDiffStrategy;
import com.example.editor.audit.JsonlAuditLogStore;
import com.example.editor.audit.LocalAuditActorContext;
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
import com.example.editor.scope.LocalWorkspaceContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Regresión del defecto #10 de HALLAZGOS_PRODUCCION: aplicar y revertir una transacción de capas portables movía los
 * ficheros sin dejar rastro en la auditoría. Aquí el servicio real, el observador de auditoría real, el publicador
 * asíncrono real y el almacén JSONL real escriben en un directorio temporal; el oráculo lee el fichero JSONL crudo.
 */
class CapabilityLayerTransactionAuditIntegrationTests {
    @TempDir Path temporary;
    private final ObjectMapper mapper = JsonMapper.builder().findAndAddModules().build();
    private DefaultAuditEventPublisher publisher;
    private CapabilityLayerTransactionService service;
    private Path metamodels;

    @BeforeEach
    void setUp() {
        AuditLogProperties audit = new AuditLogProperties();
        audit.setStorageRoot(temporary.resolve("audit-log"));
        audit.setRetentionEnabled(false);
        AuditRedactionRegistry redaction = new AuditRedactionRegistry(List.of());
        publisher = new DefaultAuditEventPublisher(audit, new LocalAuditActorContext(), new LocalWorkspaceContext(),
                new AuditSnapshotService(mapper, audit, redaction),
                new AuditDiffStrategyRegistry(List.of(new GenericAuditDiffStrategy(audit, redaction)), redaction),
                new JsonlAuditLogStore(audit, mapper));
        publisher.afterPropertiesSet();

        metamodels = temporary.resolve("metamodels");
        CapabilityLayerTransactionProperties properties = new CapabilityLayerTransactionProperties();
        properties.setStorageRoot(temporary.resolve("transactions").toString());
        ProjectWorkspaceContributionRegistry registry = new ProjectWorkspaceContributionRegistry(List.of()) {
            @Override public Snapshot snapshot(com.example.editor.project.ProjectLayout layout) {
                return new Snapshot(List.of(), Map.of(ArtifactKind.METAMODEL, metamodels));
            }
        };
        service = new CapabilityLayerTransactionService(properties, new CapabilityPackageChecker(), registry,
                new ProjectArtifactClassifier(), mapper,
                List.of(new AuditCanonicalDocumentMutationObserver(publisher, new AuditCapturePolicy(audit))));
    }

    @AfterEach
    void tearDown() {
        publisher.destroy();
    }

    @Test
    void applyingAndRollingBackALayerLeavesOneAuditLineEach() throws Exception {
        String documentPath = "metamodels/people.metamodel.json";
        CapabilityLayerTransactionResult result = service.apply(new CapabilityLayerTransactionRequest(
                capabilityPackage(documentPath), "people", Map.of(), List.of("METAMODEL"), true));
        publisher.flushPending();

        List<JsonNode> afterApply = auditLines();
        assertThat(afterApply).singleElement().satisfies(line -> {
            assertThat(line.get("activityType").asString()).isEqualTo("portability.capability-layer.created");
            assertThat(line.get("action").asString()).isEqualTo(AuditActions.CREATE);
            assertThat(line.get("resource").get("resourceId").asString()).isEqualTo(documentPath);
        });

        service.rollback(new CapabilityLayerRollbackRequest(result.transactionId(), List.of(), result.appliedEtags()));
        publisher.flushPending();

        List<JsonNode> afterRollback = auditLines();
        assertThat(afterRollback).hasSize(2);
        assertThat(afterRollback.get(1).get("activityType").asString()).isEqualTo("portability.capability-layer.deleted");
        assertThat(afterRollback.get(1).get("resource").get("resourceId").asString()).isEqualTo(documentPath);
        assertThat(metamodels.resolve("people.metamodel.json")).doesNotExist();
    }

    @Test
    void aPreviewOnlyRequestWritesNothingAndAuditsNothing() throws Exception {
        service.apply(new CapabilityLayerTransactionRequest(capabilityPackage("metamodels/people.metamodel.json"),
                "people", Map.of(), List.of("METAMODEL"), false));
        publisher.flushPending();

        assertThat(temporary.resolve("audit-log")).doesNotExist();
        assertThat(metamodels.resolve("people.metamodel.json")).doesNotExist();
    }

    /** Lee todas las líneas JSONL crudas de la única partición de auditoría, sin usar la API de consulta del almacén. */
    private List<JsonNode> auditLines() throws Exception {
        Path partitions = temporary.resolve("audit-log");
        Path file;
        try (var stream = Files.list(partitions)) { file = stream.findFirst().orElseThrow(); }
        List<JsonNode> result = new ArrayList<>();
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) if (!line.isBlank()) result.add(mapper.readTree(line));
        return result;
    }

    private HydraCapabilityPackage capabilityPackage(String path) throws Exception {
        HydraCapabilityPackageManifest manifest = new HydraCapabilityPackageManifest("1.0", "people-layers", "people layers", "1.0.0", "people",
                new MetamodelOrigin(MetamodelOriginKind.OWNED, null, "", "", "", "", "", Map.of(), Map.of()),
                List.of(new CapabilitySelection("metamodel", HydraCapabilityKind.METAMODEL, path, "2.1", true, false, Map.of())),
                List.of(), List.of(), Map.of(), Map.of());
        return new HydraCapabilityPackage(manifest, Map.of(path, mapper.readTree(
                "{\"schemaVersion\":\"2.1\",\"id\":\"people\",\"name\":\"people\",\"classes\":[],\"enums\":[],\"dataTypes\":[],\"relations\":[],\"annotations\":{}}")), Map.of());
    }
}
