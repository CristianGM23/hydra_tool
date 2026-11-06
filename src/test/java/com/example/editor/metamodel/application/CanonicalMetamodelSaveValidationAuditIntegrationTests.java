package com.example.editor.metamodel.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.editor.audit.AuditActions;
import com.example.editor.audit.AuditDiffStrategyRegistry;
import com.example.editor.audit.AuditLogProperties;
import com.example.editor.audit.AuditRedactionRegistry;
import com.example.editor.audit.AuditSnapshotService;
import com.example.editor.audit.DefaultAuditEventPublisher;
import com.example.editor.audit.GenericAuditDiffStrategy;
import com.example.editor.audit.JsonlAuditLogStore;
import com.example.editor.audit.LocalAuditActorContext;
import com.example.editor.config.StorageProperties;
import com.example.editor.metamodel.canonical.CanonicalMetamodelJsonCodec;
import com.example.editor.metamodel.canonical.CanonicalMetamodelProperties;
import com.example.editor.metamodel.canonical.CanonicalMetamodelTestFixtures;
import com.example.editor.metamodel.canonical.JsonCanonicalMetamodelRepository;
import com.example.editor.metamodel.core.MetaClass;
import com.example.editor.metamodel.core.MetaClassKind;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.core.MetaRelation;
import com.example.editor.metamodel.core.MetaRelationKind;
import com.example.editor.metamodel.target.JsonTargetConfigurationRepository;
import com.example.editor.metamodel.validation.NeutralMetamodelValidator;
import com.example.editor.scope.LocalWorkspaceContext;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * x_metamodel_save_and_cascade.val / .doc / .aud - saving a metamodel: blocking structural validation, versioned
 * document, audit entry.
 *
 * Real {@link CanonicalMetamodelApplicationService} + real neutral validator + real JSON repository on a
 * {@code @TempDir}, wired to the real asynchronous audit pipeline ({@link DefaultAuditEventPublisher} ->
 * {@link JsonlAuditLogStore}) exactly as the repository publishes it in production. The oracle is independent:
 * metamodel files and the JSONL audit partition are read from disk and the SHA-256 ETag is recomputed here.
 */
class CanonicalMetamodelSaveValidationAuditIntegrationTests {

    @TempDir
    Path temporaryDirectory;

    private JsonMapper mapper;
    private StorageProperties storage;
    private DefaultAuditEventPublisher publisher;
    private CanonicalMetamodelApplicationService service;

    @BeforeEach
    void setUp() {
        mapper = JsonMapper.builder().findAndAddModules().build();
        storage = new StorageProperties(temporaryDirectory.resolve("metamodels"), temporaryDirectory.resolve("models"),
                temporaryDirectory.resolve("notations"), temporaryDirectory.resolve("validations"),
                temporaryDirectory.resolve("quickfixes"), temporaryDirectory.resolve("generators"),
                temporaryDirectory.resolve("assets"), temporaryDirectory.resolve("exports"), true);

        AuditLogProperties auditProperties = new AuditLogProperties();
        auditProperties.setStorageRoot(temporaryDirectory.resolve("audit-log"));
        auditProperties.setRetentionEnabled(false);
        AuditRedactionRegistry redaction = new AuditRedactionRegistry(List.of());
        AuditDiffStrategyRegistry diffStrategies = new AuditDiffStrategyRegistry(
                List.of(new GenericAuditDiffStrategy(auditProperties, redaction)), redaction);
        AuditSnapshotService snapshots = new AuditSnapshotService(mapper, auditProperties, redaction);
        publisher = new DefaultAuditEventPublisher(auditProperties, new LocalAuditActorContext(), new LocalWorkspaceContext(),
                snapshots, diffStrategies, new JsonlAuditLogStore(auditProperties, mapper));
        publisher.afterPropertiesSet();

        JsonCanonicalMetamodelRepository repository = new JsonCanonicalMetamodelRepository(storage,
                new CanonicalMetamodelProperties(), mapper, new CanonicalMetamodelJsonCodec(mapper),
                new JsonTargetConfigurationRepository(storage, mapper), publisher);
        service = new CanonicalMetamodelApplicationService(repository, new NeutralMetamodelValidator());
    }

    @AfterEach
    void tearDown() {
        publisher.destroy();
    }

    private List<Path> metamodelFiles() throws Exception {
        Path directory = storage.metamodels();
        if (!Files.isDirectory(directory)) return List.of();
        try (var stream = Files.list(directory)) {
            return stream.filter(Files::isRegularFile).toList();
        }
    }

    private List<JsonNode> auditLines() throws Exception {
        publisher.flushPending();
        Path directory = temporaryDirectory.resolve("audit-log");
        if (!Files.isDirectory(directory)) return List.of();
        List<JsonNode> lines = new ArrayList<>();
        try (var stream = Files.list(directory)) {
            for (Path partition : stream.filter(Files::isRegularFile).toList()) {
                for (String line : Files.readAllLines(partition, StandardCharsets.UTF_8)) {
                    if (!line.isBlank()) lines.add(mapper.readTree(line));
                }
            }
        }
        return lines;
    }

    private static String sha256Etag(byte[] content) throws Exception {
        return "\"" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content)) + "\"";
    }

    private static MetaModel cyclicInheritance(String id) {
        MetaClass first = new MetaClass("class-a", "A", MetaClassKind.CLASS, List.of(), List.of(), Map.of());
        MetaClass second = new MetaClass("class-b", "B", MetaClassKind.CLASS, List.of(), List.of(), Map.of());
        MetaRelation firstEdge = new MetaRelation("inheritance-a", "", "class-a", "class-b", MetaRelationKind.INHERITANCE,
                null, null, "", true, true, Map.of());
        MetaRelation secondEdge = new MetaRelation("inheritance-b", "", "class-b", "class-a", MetaRelationKind.INHERITANCE,
                null, null, "", true, true, Map.of());
        return new MetaModel(MetaModel.CURRENT_SCHEMA_VERSION, id, "Cyclic " + id, List.of(first, second), List.of(),
                List.of(), List.of(firstEdge, secondEdge), Map.of());
    }

    @Test
    @DisplayName("x_metamodel_save_and_cascade.val: a cyclic inheritance is rejected, no document and no audit line are written")
    void cyclicInheritanceIsRejectedWithoutDocumentNorAuditLine() throws Exception {
        assertThatThrownBy(() -> service.create(cyclicInheritance("cyclic")))
                .isInstanceOf(MetamodelApplicationException.class)
                .satisfies(e -> {
                    MetamodelApplicationException exception = (MetamodelApplicationException) e;
                    assertThat(exception.getReason()).isEqualTo(MetamodelApplicationException.Reason.VALIDATION_ERROR);
                    assertThat(exception.getMessage()).contains("INHERITANCE_CYCLE");
                });

        assertThat(metamodelFiles()).isEmpty();
        assertThat(auditLines()).isEmpty();
        assertThat(Files.exists(temporaryDirectory.resolve("audit-log"))).isFalse();
    }

    @Test
    @DisplayName("x_metamodel_save_and_cascade.val: a cyclic update of an already stored metamodel leaves the stored bytes untouched")
    void cyclicUpdateLeavesTheStoredDocumentUntouched() throws Exception {
        MetamodelSaveResponse saved = service.create(CanonicalMetamodelTestFixtures.valid("keep"));
        byte[] before = Files.readAllBytes(metamodelFiles().get(0));
        int auditBefore = auditLines().size();

        assertThatThrownBy(() -> service.replace("keep", cyclicInheritance("keep"), saved.etag()))
                .isInstanceOf(MetamodelApplicationException.class)
                .satisfies(e -> assertThat(((MetamodelApplicationException) e).getReason())
                        .isEqualTo(MetamodelApplicationException.Reason.VALIDATION_ERROR));

        assertThat(Files.readAllBytes(metamodelFiles().get(0))).isEqualTo(before);
        assertThat(auditLines()).hasSize(auditBefore);
    }

    @Test
    @DisplayName("x_metamodel_save_and_cascade.doc: a valid metamodel is stored as a versioned document whose ETag is the SHA-256 of its bytes")
    void validMetamodelIsStoredAsVersionedDocument() throws Exception {
        MetamodelSaveResponse response = service.create(CanonicalMetamodelTestFixtures.valid("sample"));

        List<Path> files = metamodelFiles();
        assertThat(files).hasSize(1);
        byte[] raw = Files.readAllBytes(files.get(0));
        JsonNode document = mapper.readTree(raw);
        assertThat(document.get("id").asString()).isEqualTo("sample");
        assertThat(document.get("schemaVersion").asString()).isEqualTo(MetaModel.CURRENT_SCHEMA_VERSION);
        assertThat(document.get("name").asString()).isEqualTo("Model sample");
        assertThat(response.etag()).isEqualTo(sha256Etag(raw));
        assertThat(files.get(0).getFileName().toString()).startsWith("sample");
    }

    @Test
    @DisplayName("x_metamodel_save_and_cascade.doc: a second save with an obsolete ETag is a conflict and the disk stays intact")
    void obsoleteEtagIsAConflictAndDiskIsIntact() throws Exception {
        MetamodelSaveResponse first = service.create(CanonicalMetamodelTestFixtures.valid("sample", "First", Map.of()));
        MetamodelSaveResponse second = service.replace("sample", CanonicalMetamodelTestFixtures.valid("sample", "Second", Map.of()), first.etag());
        byte[] afterSecond = Files.readAllBytes(metamodelFiles().get(0));
        assertThat(second.etag()).isNotEqualTo(first.etag()).isEqualTo(sha256Etag(afterSecond));
        int auditBefore = auditLines().size();

        assertThatThrownBy(() -> service.replace("sample", CanonicalMetamodelTestFixtures.valid("sample", "Stale", Map.of()), first.etag()))
                .isInstanceOf(MetamodelApplicationException.class)
                .satisfies(e -> assertThat(((MetamodelApplicationException) e).getReason())
                        .isEqualTo(MetamodelApplicationException.Reason.PRECONDITION_FAILED));

        assertThat(Files.readAllBytes(metamodelFiles().get(0))).isEqualTo(afterSecond);
        assertThat(new String(afterSecond, StandardCharsets.UTF_8)).contains("Second").doesNotContain("Stale");
        assertThat(auditLines()).hasSize(auditBefore);
    }

    @Test
    @DisplayName("x_metamodel_save_and_cascade.aud: each successful save appends one metamodel.created / metamodel.updated JSONL line")
    void successfulSavesAppendOneAuditLineEach() throws Exception {
        MetamodelSaveResponse first = service.create(CanonicalMetamodelTestFixtures.valid("sample", "First", Map.of()));
        service.replace("sample", CanonicalMetamodelTestFixtures.valid("sample", "Second", Map.of()), first.etag());

        List<JsonNode> lines = auditLines();
        assertThat(lines).hasSize(2);
        assertThat(lines).extracting(line -> line.get("activityType").asString())
                .containsExactlyInAnyOrder("metamodel.created", "metamodel.updated");
        for (JsonNode line : lines) {
            assertThat(line.get("moduleId").asString()).isEqualTo("metamodel");
            assertThat(line.get("resource").get("resourceId").asString()).isEqualTo("sample");
        }
        JsonNode created = lines.stream().filter(l -> "metamodel.created".equals(l.get("activityType").asString())).findFirst().orElseThrow();
        JsonNode updated = lines.stream().filter(l -> "metamodel.updated".equals(l.get("activityType").asString())).findFirst().orElseThrow();
        assertThat(created.get("action").asString()).isEqualTo(AuditActions.CREATE);
        assertThat(updated.get("action").asString()).isEqualTo(AuditActions.UPDATE);
        assertThat(created.get("resource").get("displayName").asString()).isEqualTo("First");
        assertThat(updated.get("resource").get("displayName").asString()).isEqualTo("Second");
    }
}
