package com.example.editor.audit;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.editor.persistence.CanonicalDocumentMutation;
import com.example.editor.persistence.CanonicalDocumentMutationKind;
import com.example.editor.scope.LocalWorkspaceContext;
import com.example.editor.scope.ResourceScope;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Evidencia FUERTE real, extremo a extremo, para la etapa "c" (anotar en la auditoría) de
 * persistence_save_etag: una mutación canónica real (sin mocks) atraviesa
 * {@link AuditCanonicalDocumentMutationObserver} -> {@link DefaultAuditEventPublisher}
 * (pipeline asíncrono real, con su worker thread real) -> {@link GenericAuditDiffStrategy}
 * (diff real, no simulado) -> {@link JsonlAuditLogStore} (persistencia real en disco).
 * El oráculo es independiente: se lee el fichero JSONL crudo en disco, sin pasar por la
 * API de consulta del propio store.
 */
class CanonicalDocumentMutationAuditIntegrationTests {

    @TempDir
    Path temporaryDirectory;

    private JsonMapper mapper;
    private AuditLogProperties properties;
    private JsonlAuditLogStore store;
    private DefaultAuditEventPublisher publisher;
    private AuditCanonicalDocumentMutationObserver observer;

    @BeforeEach
    void setUp() {
        mapper = JsonMapper.builder().findAndAddModules().build();
        properties = new AuditLogProperties();
        properties.setStorageRoot(temporaryDirectory.resolve("audit-log"));
        properties.setRetentionEnabled(false);

        AuditRedactionRegistry redaction = new AuditRedactionRegistry(List.of());
        AuditDiffStrategyRegistry diffStrategies = new AuditDiffStrategyRegistry(
                List.of(new GenericAuditDiffStrategy(properties, redaction)), redaction);
        AuditSnapshotService snapshots = new AuditSnapshotService(mapper, properties, redaction);
        LocalAuditActorContext actorContext = new LocalAuditActorContext();
        LocalWorkspaceContext workspaceContext = new LocalWorkspaceContext();
        store = new JsonlAuditLogStore(properties, mapper);

        publisher = new DefaultAuditEventPublisher(properties, actorContext, workspaceContext,
                snapshots, diffStrategies, store);
        publisher.afterPropertiesSet();

        AuditCapturePolicy capturePolicy = new AuditCapturePolicy(properties);
        observer = new AuditCanonicalDocumentMutationObserver(publisher, capturePolicy);
    }

    @AfterEach
    void tearDown() {
        publisher.destroy();
    }

    private Path singlePartitionFile() throws Exception {
        Path partitionDir = temporaryDirectory.resolve("audit-log");
        try (var stream = Files.list(partitionDir)) {
            return stream.findFirst().orElseThrow();
        }
    }

    @Test
    void realCanonicalUpdateMutationIsObservedDiffedAndPersistedToAnIndependentlyReadableAuditFile() throws Exception {
        CanonicalDocumentMutation mutation = new CanonicalDocumentMutation(
                "metamodel.document", ResourceScope.local(), "mm-42",
                CanonicalDocumentMutationKind.UPDATE,
                Map.of("name", "Metamodelo original", "version", 1),
                Map.of("name", "Metamodelo actualizado", "version", 2));

        observer.onMutation(mutation);
        publisher.flushPending();

        Path partitionFile = singlePartitionFile();
        List<String> lines = Files.readAllLines(partitionFile, StandardCharsets.UTF_8);
        assertThat(lines).hasSize(1);

        JsonNode persisted = mapper.readTree(lines.get(0));
        assertThat(persisted.get("activityType").asString()).isEqualTo("metamodel.document.updated");
        assertThat(persisted.get("moduleId").asString()).isEqualTo("metamodel");
        assertThat(persisted.get("action").asString()).isEqualTo(AuditActions.UPDATE);
        assertThat(persisted.get("workspaceId").asString()).isEqualTo(ResourceScope.LOCAL_WORKSPACE_ID);
        assertThat(persisted.get("resource").get("resourceId").asString()).isEqualTo("mm-42");
        assertThat(persisted.get("resource").get("displayName").asString()).isEqualTo("Metamodelo actualizado");

        // El diff real (GenericAuditDiffStrategy) debe reflejar el cambio de campo real producido.
        List<JsonNode> changes = persisted.get("changes").valueStream().toList();
        assertThat(changes).anySatisfy(change -> {
            assertThat(change.get("path").asString()).isEqualTo("version");
            assertThat(change.get("beforeValue").asString()).isEqualTo("1");
            assertThat(change.get("afterValue").asString()).isEqualTo("2");
        });
    }

    @Test
    void realCanonicalCreateMutationIsRecordedWithoutABeforeState() throws Exception {
        CanonicalDocumentMutation mutation = new CanonicalDocumentMutation(
                "metamodel.document", ResourceScope.local(), "mm-new",
                CanonicalDocumentMutationKind.CREATE, null, Map.of("name", "Nuevo metamodelo"));

        observer.onMutation(mutation);
        publisher.flushPending();

        Path partitionFile = singlePartitionFile();
        JsonNode persisted = mapper.readTree(Files.readAllLines(partitionFile, StandardCharsets.UTF_8).get(0));
        assertThat(persisted.get("activityType").asString()).isEqualTo("metamodel.document.created");
        assertThat(persisted.get("action").asString()).isEqualTo(AuditActions.CREATE);
        assertThat(persisted.get("resource").get("resourceId").asString()).isEqualTo("mm-new");
    }

    @Test
    void mutationsInANamespaceIgnoredByPolicyProduceNoAuditFileAtAll() {
        // "test.documents" está en la lista por defecto de ignoredNamespaces de AuditLogProperties.
        CanonicalDocumentMutation mutation = new CanonicalDocumentMutation(
                "test.documents", ResourceScope.local(), "doc-1",
                CanonicalDocumentMutationKind.UPDATE, Map.of("a", 1), Map.of("a", 2));

        observer.onMutation(mutation);
        publisher.flushPending();

        Path partitionDir = temporaryDirectory.resolve("audit-log");
        assertThat(Files.exists(partitionDir)).isFalse();
    }
}
