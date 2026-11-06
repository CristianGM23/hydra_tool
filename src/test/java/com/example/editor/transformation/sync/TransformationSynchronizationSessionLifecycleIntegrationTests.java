package com.example.editor.transformation.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.editor.artifact.transaction.HydraArtifactTransactionService;
import com.example.editor.config.StorageProperties;
import com.example.editor.metamodel.application.CanonicalMetamodelApplicationService;
import com.example.editor.metamodel.canonical.CanonicalMetamodelProperties;
import com.example.editor.metamodel.canonical.JsonCanonicalMetamodelRepository;
import com.example.editor.metamodel.validation.MetamodelValidationFingerprint;
import com.example.editor.metamodel.validation.NeutralMetamodelValidator;
import com.example.editor.persistence.LocalCanonicalDocumentStoreFactory;
import com.example.editor.semantic.expression.ExpressionDependencyAnalyzer;
import com.example.editor.semantic.expression.ExpressionEvaluator;
import com.example.editor.semantic.expression.ExpressionTestFixtures;
import com.example.editor.semantic.expression.ExpressionTypeChecker;
import com.example.editor.testing.fault.HydraFaultInjectionProperties;
import com.example.editor.testing.fault.HydraFaultInjectionService;
import com.example.editor.transformation.TransformationApplicationException;
import com.example.editor.transformation.TransformationApplicationService;
import com.example.editor.transformation.TransformationDefinitionChecker;
import com.example.editor.transformation.TransformationProperties;
import com.example.editor.transformation.core.TransformationBinding;
import com.example.editor.transformation.core.TransformationBindingKind;
import com.example.editor.transformation.core.TransformationConflictPolicy;
import com.example.editor.transformation.core.TransformationDefinitionDocument;
import com.example.editor.transformation.core.TransformationDirectionality;
import com.example.editor.transformation.core.TransformationEndpoint;
import com.example.editor.transformation.core.TransformationEndpointRole;
import com.example.editor.transformation.core.TransformationExecutionDirection;
import com.example.editor.transformation.core.TransformationRule;
import com.example.editor.transformation.core.TransformationRuleDirection;
import com.example.editor.transformation.core.TransformationSynchronizationPolicy;
import com.example.editor.transformation.core.TransformationTargetPattern;
import com.example.editor.transformation.core.TransformationTracePolicy;
import com.example.editor.transformation.persistence.JsonTransformationRepository;
import com.example.editor.transformation.runtime.CanonicalTransformationRuntime;
import com.example.editor.transformation.runtime.TransformationExecutionException;
import com.example.editor.transformation.runtime.TransformationPlanCompiler;
import com.example.editor.transformation.runtime.TransformationRuntimeService;
import com.example.editor.transformation.runtime.TransformationSynchronizationSide;
import com.example.editor.transformation.testing.persistence.JsonTransformationTestCaseRepository;
import com.example.editor.validation.model.CanonicalModelDocument;
import com.example.editor.validation.model.CanonicalModelElement;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Evidencia real, sin mocks, del ciclo de vida de una sesion de sincronizacion incremental
 * bidireccional (transformation_sync.cr/sy/blk/ap/rev/rb/rs y
 * x_transformation_run_and_sync.ses/sy/ap/sv). Servicio de sesiones real, repositorio JSON real en
 * @TempDir, servicios de aplicacion y runtime reales y una transformacion Person&lt;-&gt;Person
 * bidireccional con rastro. Los oraculos son valores literales (nombres de los elementos),
 * identificadores de punto de control y ficheros JSON leidos del disco.
 */
class TransformationSynchronizationSessionLifecycleIntegrationTests {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    private static final String ALICE = "object:alice";
    private static final String BOB = "object:bob";
    private static final String NAME = ExpressionTestFixtures.NAME;

    @Test
    @DisplayName("transformation_sync.cr/sy/ap/rev + x_transformation_run_and_sync.ses/sy/ap/sv: crear, sin cambios, editar un lado, recargar")
    void createsASessionDetectsNoChangesAndPropagatesOnlyTheEditedElement(@TempDir Path temporary) throws IOException {
        Env env = Env.open(temporary, true, "transformation-sync-lifecycle", TransformationConflictPolicy.FAIL_ON_CONFLICT);

        TransformationSynchronizationSessionMutationResult created = env.sessions().create(env.transformationId(),
                new TransformationSynchronizationSessionCreateRequest(sourceModel(), seedTarget(), TransformationSynchronizationSide.SOURCE));
        TransformationSynchronizationSession first = created.session();
        String sessionId = first.id();

        // cr / ses: revision 1, ambas lineas base, rastro y primer punto de control.
        assertThat(created.advanced()).isTrue();
        assertThat(first.revision()).isEqualTo(1);
        assertThat(first.createdAtEpochMillis()).isEqualTo(CLOCK.millis());
        assertThat(first.sourceBaseline().elements()).isEqualTo(sourceModel().elements());
        assertThat(first.targetBaseline().elements()).extracting(element -> element.attributes().get(NAME))
                .containsExactlyInAnyOrder("Alice", "Bob");
        assertThat(first.baselineTrace().links()).hasSize(2);
        List<TransformationSynchronizationCheckpoint> historyAfterCreate = env.sessions().history(env.transformationId(), sessionId);
        assertThat(historyAfterCreate).extracting(TransformationSynchronizationCheckpoint::revision, TransformationSynchronizationCheckpoint::operation)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(1L, "INITIALIZED_SOURCE"));
        assertThat(JsonDisk.revisionOf(env.properties().getSynchronizationSessionDirectory(), sessionId)).isEqualTo(1L);
        assertThat(JsonDisk.fileNames(env.properties().getSynchronizationHistoryDirectory()))
                .singleElement().asString().startsWith(sessionId + ".r1");

        // sy: sin cambios en ningun lado -> noOp, sin subir la revision ni cambiar la ETag.
        TransformationSynchronizationSessionMutationResult unchanged = env.sessions().synchronize(env.transformationId(), sessionId,
                new TransformationSynchronizationSessionUpdateRequest(first.sourceBaseline(), first.targetBaseline()), created.etag());
        assertThat(unchanged.synchronization().noOp()).isTrue();
        assertThat(unchanged.advanced()).isFalse();
        assertThat(unchanged.session().revision()).isEqualTo(1);
        assertThat(unchanged.etag()).isEqualTo(created.etag());
        assertThat(env.sessions().history(env.transformationId(), sessionId)).hasSize(1);

        // sy / ap / rev: se edita Alice en el origen; solo su pareja del destino cambia y la revision sube a 2.
        CanonicalModelDocument editedSource = renamed(first.sourceBaseline(), ALICE, "Alicia");
        TransformationSynchronizationSessionMutationResult advanced = env.sessions().synchronize(env.transformationId(), sessionId,
                new TransformationSynchronizationSessionUpdateRequest(editedSource, first.targetBaseline()), created.etag());

        assertThat(advanced.advanced()).isTrue();
        assertThat(advanced.session().revision()).isEqualTo(2);
        assertThat(advanced.etag()).isNotEqualTo(created.etag());
        assertThat(advanced.synchronization().blocked()).isFalse();
        assertThat(advanced.synchronization().appliedDirections()).containsExactly(TransformationExecutionDirection.FORWARD);
        assertThat(advanced.synchronization().sourceDelta().modifiedElementIds()).containsExactly(ALICE);
        assertThat(advanced.synchronization().targetDelta().emptyDelta()).isTrue();
        String aliceTargetId = targetIdFor(first.targetBaseline(), ALICE);
        String bobTargetId = targetIdFor(first.targetBaseline(), BOB);
        CanonicalModelDocument targetAfter = advanced.session().targetBaseline();
        assertThat(nameOf(targetAfter, aliceTargetId)).isEqualTo("Alicia");
        assertThat(targetAfter.findElement(bobTargetId)).isEqualTo(first.targetBaseline().findElement(bobTargetId));
        assertThat(targetAfter.id()).isEqualTo(first.targetBaseline().id());
        assertThat(nameOf(advanced.session().sourceBaseline(), ALICE)).isEqualTo("Alicia");

        // sv: la sesion y los puntos de control estan en disco y una instancia nueva los recarga.
        assertThat(JsonDisk.revisionOf(env.properties().getSynchronizationSessionDirectory(), sessionId)).isEqualTo(2L);
        assertThat(JsonDisk.fileNames(env.properties().getSynchronizationHistoryDirectory())).hasSize(2);
        Env reopened = Env.open(temporary, false, "transformation-sync-lifecycle", TransformationConflictPolicy.FAIL_ON_CONFLICT);
        TransformationSynchronizationSessionLoadResult reloaded = reopened.sessions().find(env.transformationId(), sessionId);
        assertThat(reloaded.session().revision()).isEqualTo(2);
        assertThat(reloaded.etag()).isEqualTo(advanced.etag());
        assertThat(nameOf(reloaded.session().targetBaseline(), aliceTargetId)).isEqualTo("Alicia");
        assertThat(reopened.sessions().history(env.transformationId(), sessionId))
                .extracting(TransformationSynchronizationCheckpoint::revision, TransformationSynchronizationCheckpoint::operation)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(2L, "SYNCHRONIZED"), org.assertj.core.groups.Tuple.tuple(1L, "INITIALIZED_SOURCE"));
    }

    @Test
    @DisplayName("transformation_sync.blk + ap: con politica de parada el conflicto bloquea sin excepcion; los cambios disjuntos se fusionan")
    void stopPolicyBlocksAConflictWithoutAdvancingAndMergesDisjointEdits(@TempDir Path temporary) throws IOException {
        Env env = Env.open(temporary, true, "transformation-sync-stop", TransformationConflictPolicy.FAIL_ON_CONFLICT);
        TransformationSynchronizationSessionMutationResult created = env.createFromSource();
        TransformationSynchronizationSession first = created.session();
        String aliceTargetId = targetIdFor(first.targetBaseline(), ALICE);
        String bobTargetId = targetIdFor(first.targetBaseline(), BOB);

        // Ambos lados editan a Alice -> conflicto de atributo; resultado bloqueado, nada avanza.
        CanonicalModelDocument sourceEdit = renamed(first.sourceBaseline(), ALICE, "Alicia");
        CanonicalModelDocument targetEdit = renamed(first.targetBaseline(), aliceTargetId, "Alison");
        TransformationSynchronizationSessionMutationResult blocked = env.sessions().synchronize(env.transformationId(), first.id(),
                new TransformationSynchronizationSessionUpdateRequest(sourceEdit, targetEdit), created.etag());

        assertThat(blocked.synchronization().blocked()).isTrue();
        assertThat(blocked.synchronization().conflictResolution()).isEqualTo("BLOCKED_BY_CONFLICT");
        assertThat(blocked.advanced()).isFalse();
        assertThat(blocked.session().revision()).isEqualTo(1);
        assertThat(blocked.etag()).isEqualTo(created.etag());
        assertThat(blocked.synchronization().conflicts()).singleElement().satisfies(conflict -> {
            assertThat(conflict.sourceElementId()).isEqualTo(ALICE);
            assertThat(conflict.targetElementId()).isEqualTo(aliceTargetId);
            assertThat(conflict.sourceFeatures()).containsExactly(NAME);
            assertThat(conflict.targetFeatures()).containsExactly(NAME);
        });
        assertThat(blocked.synchronization().sourceModel()).isEqualTo(sourceEdit);
        assertThat(blocked.synchronization().targetModel()).isEqualTo(targetEdit);
        assertThat(JsonDisk.revisionOf(env.properties().getSynchronizationSessionDirectory(), first.id())).isEqualTo(1L);
        assertThat(JsonDisk.fileNames(env.properties().getSynchronizationHistoryDirectory())).hasSize(1);

        // Elementos distintos en cada lado: no hay conflicto, se propagan las dos direcciones.
        CanonicalModelDocument disjointTarget = renamed(first.targetBaseline(), bobTargetId, "Robert");
        TransformationSynchronizationSessionMutationResult merged = env.sessions().synchronize(env.transformationId(), first.id(),
                new TransformationSynchronizationSessionUpdateRequest(sourceEdit, disjointTarget), created.etag());

        assertThat(merged.advanced()).isTrue();
        assertThat(merged.session().revision()).isEqualTo(2);
        assertThat(merged.synchronization().conflicts()).isEmpty();
        assertThat(merged.synchronization().appliedDirections())
                .containsExactly(TransformationExecutionDirection.FORWARD, TransformationExecutionDirection.REVERSE);
        assertThat(nameOf(merged.session().sourceBaseline(), ALICE)).isEqualTo("Alicia");
        assertThat(nameOf(merged.session().sourceBaseline(), BOB)).isEqualTo("Robert");
        assertThat(nameOf(merged.session().targetBaseline(), aliceTargetId)).isEqualTo("Alicia");
        assertThat(nameOf(merged.session().targetBaseline(), bobTargetId)).isEqualTo("Robert");
    }

    @Test
    @DisplayName("transformation_sync.ap + rev: con politica «gana el origen» el origen se impone y el cambio del destino se descarta")
    void sourceWinsPolicyAppliesTheSourceEditAndDiscardsTheTargetEdit(@TempDir Path temporary) {
        Env env = Env.open(temporary, true, "transformation-sync-source-wins", TransformationConflictPolicy.FORWARD_WINS);
        TransformationSynchronizationSessionMutationResult created = env.createFromSource();
        TransformationSynchronizationSession first = created.session();
        String aliceTargetId = targetIdFor(first.targetBaseline(), ALICE);

        TransformationSynchronizationSessionMutationResult result = env.sessions().synchronize(env.transformationId(), first.id(),
                new TransformationSynchronizationSessionUpdateRequest(renamed(first.sourceBaseline(), ALICE, "Alicia"),
                        renamed(first.targetBaseline(), aliceTargetId, "Alison")), created.etag());

        assertThat(result.advanced()).isTrue();
        assertThat(result.session().revision()).isEqualTo(2);
        assertThat(result.synchronization().conflicts()).hasSize(1);
        assertThat(result.synchronization().conflictResolution()).isEqualTo("FORWARD_WINS");
        assertThat(result.synchronization().appliedDirections()).contains(TransformationExecutionDirection.FORWARD);
        assertThat(nameOf(result.session().targetBaseline(), aliceTargetId)).isEqualTo("Alicia");
        assertThat(nameOf(result.session().sourceBaseline(), ALICE)).isEqualTo("Alicia");
        assertThat(result.session().targetBaseline().elements()).extracting(element -> element.attributes().get(NAME))
                .doesNotContain("Alison");
    }

    @Test
    @DisplayName("transformation_sync.ap + rev: con politica «gana el destino» el destino se impone y el cambio del origen se descarta")
    void targetWinsPolicyAppliesTheTargetEditAndDiscardsTheSourceEdit(@TempDir Path temporary) {
        Env env = Env.open(temporary, true, "transformation-sync-target-wins", TransformationConflictPolicy.REVERSE_WINS);
        TransformationSynchronizationSessionMutationResult created = env.createFromSource();
        TransformationSynchronizationSession first = created.session();
        String aliceTargetId = targetIdFor(first.targetBaseline(), ALICE);

        TransformationSynchronizationSessionMutationResult result = env.sessions().synchronize(env.transformationId(), first.id(),
                new TransformationSynchronizationSessionUpdateRequest(renamed(first.sourceBaseline(), ALICE, "Alicia"),
                        renamed(first.targetBaseline(), aliceTargetId, "Alison")), created.etag());

        assertThat(result.advanced()).isTrue();
        assertThat(result.session().revision()).isEqualTo(2);
        assertThat(result.synchronization().conflictResolution()).isEqualTo("REVERSE_WINS");
        assertThat(result.synchronization().appliedDirections()).contains(TransformationExecutionDirection.REVERSE);
        assertThat(nameOf(result.session().sourceBaseline(), ALICE)).isEqualTo("Alison");
        assertThat(nameOf(result.session().targetBaseline(), aliceTargetId)).isEqualTo("Alison");
        assertThat(result.session().sourceBaseline().elements()).extracting(element -> element.attributes().get(NAME))
                .doesNotContain("Alicia");
    }

    @Test
    @DisplayName("transformation_sync.rb + rs: rebasar desde el destino sube la revision; restaurar crea una revision con las lineas base del punto 1")
    void rebaseFromTargetAndRestoreCreateNewRevisions(@TempDir Path temporary) {
        Env env = Env.open(temporary, true, "transformation-sync-rebase", TransformationConflictPolicy.FAIL_ON_CONFLICT);
        TransformationSynchronizationSessionMutationResult created = env.createFromSource();
        TransformationSynchronizationSession first = created.session();
        String bobTargetId = targetIdFor(first.targetBaseline(), BOB);
        TransformationSynchronizationSessionMutationResult second = env.sessions().synchronize(env.transformationId(), first.id(),
                new TransformationSynchronizationSessionUpdateRequest(renamed(first.sourceBaseline(), ALICE, "Alicia"), first.targetBaseline()),
                created.etag());

        // rb: la linea base se rehace desde el destino editado (Bob -> Robert); revision 3.
        CanonicalModelDocument editedTarget = renamed(second.session().targetBaseline(), bobTargetId, "Robert");
        TransformationSynchronizationSessionMutationResult rebased = env.sessions().rebase(env.transformationId(), first.id(),
                new TransformationSynchronizationSessionCreateRequest(second.session().sourceBaseline(), editedTarget,
                        TransformationSynchronizationSide.TARGET), second.etag());

        assertThat(rebased.advanced()).isTrue();
        assertThat(rebased.session().revision()).isEqualTo(3);
        assertThat(rebased.session().targetBaseline().elements()).isEqualTo(editedTarget.elements());
        assertThat(rebased.session().sourceBaseline().elements()).extracting(element -> element.attributes().get(NAME))
                .containsExactlyInAnyOrder("Alicia", "Robert");
        assertThat(rebased.session().baselineTrace().direction()).isEqualTo(TransformationExecutionDirection.REVERSE);
        assertThat(env.sessions().history(env.transformationId(), first.id()).getFirst().operation()).isEqualTo("REBASED_TARGET");

        // rs: restaurar el punto 1 crea la revision 4 con las lineas base originales.
        TransformationSynchronizationSessionMutationResult restored = env.sessions().restore(env.transformationId(), first.id(), 1L, rebased.etag());

        assertThat(restored.advanced()).isTrue();
        assertThat(restored.restoredFromRevision()).isEqualTo(1L);
        assertThat(restored.session().revision()).isEqualTo(4);
        assertThat(restored.session().sourceBaseline().id()).isEqualTo("people-source");
        assertThat(restored.session().sourceBaseline().elements()).isEqualTo(sourceModel().elements());
        assertThat(restored.session().targetBaseline().elements()).isEqualTo(first.targetBaseline().elements());
        assertThat(env.sessions().history(env.transformationId(), first.id()))
                .extracting(TransformationSynchronizationCheckpoint::revision, TransformationSynchronizationCheckpoint::operation)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(4L, "RESTORED_FROM_1"),
                        org.assertj.core.groups.Tuple.tuple(3L, "REBASED_TARGET"),
                        org.assertj.core.groups.Tuple.tuple(2L, "SYNCHRONIZED"),
                        org.assertj.core.groups.Tuple.tuple(1L, "INITIALIZED_SOURCE"));

        assertThatThrownBy(() -> env.sessions().restore(env.transformationId(), first.id(), 99L, restored.etag()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Synchronization checkpoint was not found: revision 99");
    }

    @Test
    @DisplayName("x_transformation_run_and_sync.sy/sv: ETag obsoleta, identidad o huella de metamodelo/definicion incoherentes se rechazan sin avanzar")
    void staleEtagInconsistentBaselineAndChangedDefinitionAreRejectedWithoutAdvancing(@TempDir Path temporary) {
        Env env = Env.open(temporary, true, "transformation-sync-reject", TransformationConflictPolicy.FAIL_ON_CONFLICT);
        TransformationSynchronizationSessionMutationResult created = env.createFromSource();
        TransformationSynchronizationSession first = created.session();
        String sid = first.id();
        TransformationSynchronizationSessionUpdateRequest sameModels =
                new TransformationSynchronizationSessionUpdateRequest(first.sourceBaseline(), first.targetBaseline());

        assertThatThrownBy(() -> env.sessions().synchronize(env.transformationId(), sid, sameModels, "etag-incorrecta"))
                .isInstanceOf(TransformationApplicationException.class)
                .extracting(exception -> ((TransformationApplicationException) exception).getReason())
                .isEqualTo(TransformationApplicationException.Reason.PRECONDITION_FAILED);
        assertThatThrownBy(() -> env.sessions().synchronize(env.transformationId(), sid, sameModels, " "))
                .isInstanceOf(TransformationApplicationException.class)
                .extracting(exception -> ((TransformationApplicationException) exception).getReason())
                .isEqualTo(TransformationApplicationException.Reason.PRECONDITION_FAILED);
        assertThatThrownBy(() -> env.sessions().restore(env.transformationId(), sid, 1L, "etag-incorrecta"))
                .isInstanceOf(TransformationApplicationException.class)
                .extracting(exception -> ((TransformationApplicationException) exception).getReason())
                .isEqualTo(TransformationApplicationException.Reason.PRECONDITION_FAILED);

        CanonicalModelDocument otherIdentity = new CanonicalModelDocument("1.0", "other-source", ExpressionTestFixtures.METAMODEL_ID,
                first.sourceBaseline().metamodelEtag(), "Other", first.sourceBaseline().rootElementIds(),
                first.sourceBaseline().elements(), List.of(), Map.of());
        assertThatThrownBy(() -> env.sessions().synchronize(env.transformationId(), sid,
                new TransformationSynchronizationSessionUpdateRequest(otherIdentity, first.targetBaseline()), created.etag()))
                .isInstanceOf(TransformationApplicationException.class)
                .hasMessageContaining("model identity does not match the managed synchronization baseline")
                .extracting(exception -> ((TransformationApplicationException) exception).getReason())
                .isEqualTo(TransformationApplicationException.Reason.CONFLICT);

        CanonicalModelDocument otherMetamodelRevision = new CanonicalModelDocument("1.0", first.sourceBaseline().id(),
                ExpressionTestFixtures.METAMODEL_ID, "etag-changed", "Changed", first.sourceBaseline().rootElementIds(),
                first.sourceBaseline().elements(), List.of(), Map.of());
        assertThatThrownBy(() -> env.sessions().synchronize(env.transformationId(), sid,
                new TransformationSynchronizationSessionUpdateRequest(otherMetamodelRevision, first.targetBaseline()), created.etag()))
                .isInstanceOf(TransformationExecutionException.class)
                .hasMessageContaining("metamodel revision changed after the baseline");

        assertThat(env.sessions().find(env.transformationId(), sid).session().revision()).isEqualTo(1);
        assertThat(env.sessions().find(env.transformationId(), sid).etag()).isEqualTo(created.etag());

        // La definicion cambia despues de establecer la linea base: la sesion exige rebasar.
        TransformationDefinitionDocument changed = withName(env.document(), "Renamed after baseline");
        env.transformations().replace(env.transformationId(), changed, env.transformations().find(env.transformationId()).etag());
        assertThatThrownBy(() -> env.sessions().synchronize(env.transformationId(), sid, sameModels, created.etag()))
                .isInstanceOf(TransformationApplicationException.class)
                .hasMessageContaining("Transformation definition changed after this synchronization baseline was established")
                .extracting(exception -> ((TransformationApplicationException) exception).getReason())
                .isEqualTo(TransformationApplicationException.Reason.CONFLICT);
        assertThat(JsonDisk.revisionOf(env.properties().getSynchronizationSessionDirectory(), sid)).isEqualTo(1L);
    }

    // ----------------------------------------------------------------------------------- fixtures

    private static CanonicalModelDocument sourceModel() {
        CanonicalModelElement alice = new CanonicalModelElement(ALICE, ExpressionTestFixtures.PERSON, Map.of(NAME, "Alice"), Map.of());
        CanonicalModelElement bob = new CanonicalModelElement(BOB, ExpressionTestFixtures.PERSON, Map.of(NAME, "Bob"), Map.of());
        return new CanonicalModelDocument("1.0", "people-source", ExpressionTestFixtures.METAMODEL_ID, "etag-1", "People source",
                List.of(ALICE, BOB), List.of(alice, bob), List.of(), Map.of());
    }

    private static CanonicalModelDocument seedTarget() {
        return new CanonicalModelDocument("1.0", "people-target-seed", ExpressionTestFixtures.METAMODEL_ID, "etag-1", "People target",
                List.of(), List.of(), List.of(), Map.of());
    }

    private static CanonicalModelDocument renamed(CanonicalModelDocument model, String elementId, String newName) {
        List<CanonicalModelElement> elements = model.elements().stream()
                .map(element -> element.id().equals(elementId)
                        ? new CanonicalModelElement(element.id(), element.classId(), Map.of(NAME, newName), element.annotations())
                        : element)
                .toList();
        return new CanonicalModelDocument(model.schemaVersion(), model.id(), model.metamodelId(), model.metamodelEtag(), model.name(),
                model.rootElementIds(), elements, model.relations(), model.annotations());
    }

    private static String nameOf(CanonicalModelDocument model, String elementId) {
        return String.valueOf(model.findElement(elementId).orElseThrow().attributes().get(NAME));
    }

    /** El runtime anota cada elemento destino con el identificador del elemento origen que lo genero. */
    private static String targetIdFor(CanonicalModelDocument target, String sourceElementId) {
        return target.elements().stream()
                .filter(element -> sourceElementId.equals(element.annotations().get("hydra.transformation.source")))
                .map(CanonicalModelElement::id).findFirst().orElseThrow();
    }

    private static TransformationDefinitionDocument withName(TransformationDefinitionDocument source, String name) {
        return new TransformationDefinitionDocument(source.schemaVersion(), source.id(), name, source.kind(), source.directionality(),
                source.synchronizationPolicy(), source.sources(), source.targets(), source.rules(), source.generationRules(),
                source.textToModel(), source.executionPolicy(), source.tracePolicy(), source.annotations());
    }

    private static TransformationDefinitionDocument bidirectionalDocument(String id, TransformationConflictPolicy policy) {
        TransformationRule forward = new TransformationRule("rule:person", "Person to Person", true, "source",
                ExpressionTestFixtures.PERSON, "personSource", null,
                List.of(new TransformationTargetPattern("target:person", "target", ExpressionTestFixtures.PERSON, "person", Map.of())),
                List.of(new TransformationBinding("binding:name", "person", NAME, TransformationBindingKind.DIRECT,
                        NAME, null, null, null, Map.of())),
                Map.of(), TransformationRuleDirection.FORWARD, "corr:person");
        TransformationRule reverse = new TransformationRule("rule:person-reverse", "Person to Person (reverse)", true, "target",
                ExpressionTestFixtures.PERSON, "personTarget", null,
                List.of(new TransformationTargetPattern("source:person", "source", ExpressionTestFixtures.PERSON, "person", Map.of())),
                List.of(new TransformationBinding("binding:name-reverse", "person", NAME, TransformationBindingKind.DIRECT,
                        NAME, null, null, null, Map.of())),
                Map.of(), TransformationRuleDirection.REVERSE, "corr:person");
        return new TransformationDefinitionDocument("1.1", id, "People sync " + id, null,
                TransformationDirectionality.BIDIRECTIONAL,
                new TransformationSynchronizationPolicy(policy, false, true),
                List.of(new TransformationEndpoint("source", TransformationEndpointRole.SOURCE, ExpressionTestFixtures.METAMODEL_ID, "", "source", true, Map.of())),
                List.of(new TransformationEndpoint("target", TransformationEndpointRole.TARGET, ExpressionTestFixtures.METAMODEL_ID, "", "target", true, Map.of())),
                List.of(forward, reverse), List.of(), null, null, TransformationTracePolicy.defaults(), Map.of());
    }

    /** Lectura independiente del disco: parsea los ficheros JSON que dejo el repositorio. */
    private static final class JsonDisk {
        static List<String> fileNames(Path directory) throws IOException {
            try(Stream<Path> files = Files.list(directory)) {
                return files.filter(Files::isRegularFile).map(path -> path.getFileName().toString()).sorted().toList();
            }
        }

        static long revisionOf(Path sessionDirectory, String sessionId) {
            try(Stream<Path> files = Files.list(sessionDirectory)) {
                Path file = files.filter(path -> path.getFileName().toString().startsWith(sessionId)).findFirst().orElseThrow();
                JsonNode root = JsonMapper.builder().findAndAddModules().build().readTree(Files.readString(file));
                return root.findValue("revision").longValue();
            } catch(IOException exception) {
                throw new IllegalStateException(exception);
            }
        }
    }

    private record Env(TransformationSynchronizationSessionService sessions, TransformationApplicationService transformations,
            TransformationProperties properties, String transformationId, TransformationDefinitionDocument document) {

        TransformationSynchronizationSessionMutationResult createFromSource() {
            return sessions.create(transformationId,
                    new TransformationSynchronizationSessionCreateRequest(sourceModel(), seedTarget(), TransformationSynchronizationSide.SOURCE));
        }

        static Env open(Path temporary, boolean seed, String transformationId, TransformationConflictPolicy policy) {
            JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();
            StorageProperties storage = new StorageProperties(temporary.resolve("metamodels"), temporary.resolve("models"),
                    temporary.resolve("notations"), temporary.resolve("validations"), temporary.resolve("quickfixes"),
                    temporary.resolve("generators"), temporary.resolve("assets"), temporary.resolve("exports"), true);
            CanonicalMetamodelApplicationService metamodels = new CanonicalMetamodelApplicationService(
                    new JsonCanonicalMetamodelRepository(storage, new CanonicalMetamodelProperties(), mapper), new NeutralMetamodelValidator());

            TransformationProperties properties = new TransformationProperties();
            properties.setDirectory(temporary.resolve("transformations"));
            properties.setTestDirectory(temporary.resolve("transformation-tests"));
            properties.setSynchronizationSessionDirectory(temporary.resolve("transformation-sync-sessions"));
            properties.setSynchronizationHistoryDirectory(temporary.resolve("transformation-sync-history"));

            ExpressionTypeChecker typeChecker = new ExpressionTypeChecker(new ExpressionDependencyAnalyzer());
            TransformationDefinitionChecker checker = new TransformationDefinitionChecker(typeChecker);
            HydraArtifactTransactionService transactions = new HydraArtifactTransactionService(
                    temporary.resolve("artifact-transactions").toString(), new HydraFaultInjectionService(new HydraFaultInjectionProperties()));
            JsonTransformationSynchronizationSessionRepository sessionRepository = new JsonTransformationSynchronizationSessionRepository(
                    properties, mapper, new LocalCanonicalDocumentStoreFactory());
            TransformationApplicationService transformations = new TransformationApplicationService(
                    new JsonTransformationRepository(properties, mapper), metamodels,
                    new MetamodelValidationFingerprint(mapper), checker,
                    new JsonTransformationTestCaseRepository(properties, mapper, new LocalCanonicalDocumentStoreFactory()),
                    sessionRepository, transactions);
            TransformationDefinitionDocument document = bidirectionalDocument(transformationId, policy);
            if(seed) {
                metamodels.create(ExpressionTestFixtures.metamodel());
                transformations.create(document);
            }
            TransformationRuntimeService runtime = new TransformationRuntimeService(transformations, metamodels,
                    new TransformationPlanCompiler(checker), new CanonicalTransformationRuntime(new ExpressionEvaluator(typeChecker)));
            TransformationSynchronizationSessionService sessions = new TransformationSynchronizationSessionService(
                    sessionRepository, runtime, transformations, 25, CLOCK);
            return new Env(sessions, transformations, properties, transformationId, document);
        }
    }
}
