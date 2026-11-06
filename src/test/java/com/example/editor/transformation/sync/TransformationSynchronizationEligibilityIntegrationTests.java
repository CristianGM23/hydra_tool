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
import com.example.editor.transformation.core.GenerationOverwritePolicy;
import com.example.editor.transformation.core.TransformationBinding;
import com.example.editor.transformation.core.TransformationBindingKind;
import com.example.editor.transformation.core.TransformationCheckReport;
import com.example.editor.transformation.core.TransformationConflictPolicy;
import com.example.editor.transformation.core.TransformationDefinitionDocument;
import com.example.editor.transformation.core.TransformationDiagnostic;
import com.example.editor.transformation.core.TransformationDirectionality;
import com.example.editor.transformation.core.TransformationEndpoint;
import com.example.editor.transformation.core.TransformationEndpointRole;
import com.example.editor.transformation.core.TransformationGenerationRule;
import com.example.editor.transformation.core.TransformationKind;
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

/**
 * Evidencia real, sin mocks, de la elegibilidad para abrir una sesion de sincronizacion incremental
 * (x_transformation_run_and_sync.mode). Servicios, comprobador, runtime y repositorios JSON reales en
 * @TempDir. El oraculo son codigos de diagnostico literales, mensajes literales y los directorios de
 * disco (sesiones, historial y definiciones) leidos con java.nio, no con el codigo bajo prueba.
 */
class TransformationSynchronizationEligibilityIntegrationTests {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    private static final String NAME = ExpressionTestFixtures.NAME;

    @Test
    @DisplayName("x_transformation_run_and_sync.mode: una transformacion unidireccional guardada y una M2T no abren sesion y no dejan ficheros")
    void unidirectionalAndTextGenerationTransformationsCannotOpenASynchronizationSession(@TempDir Path temporary) throws IOException {
        Env env = Env.open(temporary);
        env.transformations().create(m2m("transformation-one-way", TransformationDirectionality.UNIDIRECTIONAL, true, false));
        env.transformations().create(textGeneration("transformation-text-out"));

        assertThatThrownBy(() -> env.sessions().create("transformation-one-way", sessionRequest()))
                .isInstanceOf(TransformationExecutionException.class)
                .hasMessage("Synchronization requires a bidirectional transformation");
        assertThatThrownBy(() -> env.sessions().create("transformation-text-out", sessionRequest()))
                .isInstanceOf(TransformationExecutionException.class)
                .hasMessage("Synchronization requires a bidirectional transformation");

        // Oraculo de disco: las definiciones si estan guardadas, pero no hay ninguna sesion ni punto de control.
        assertThat(regularFiles(temporary.resolve("transformations"))).extracting(path -> path.getFileName().toString())
                .anyMatch(name -> name.contains("transformation-one-way")).anyMatch(name -> name.contains("transformation-text-out"));
        assertThat(regularFiles(env.properties().getSynchronizationSessionDirectory())).isEmpty();
        assertThat(regularFiles(env.properties().getSynchronizationHistoryDirectory())).isEmpty();
    }

    @Test
    @DisplayName("x_transformation_run_and_sync.mode: una bidireccional sin rastro o de familia M2T se rechaza al guardar y no puede abrir sesion")
    void bidirectionalWithoutTraceOrOutsideModelToModelIsRejectedBeforeAnySessionExists(@TempDir Path temporary) throws IOException {
        Env env = Env.open(temporary);
        TransformationDefinitionDocument withoutTrace = m2m("transformation-no-trace", TransformationDirectionality.BIDIRECTIONAL, false, true);
        TransformationDefinitionDocument bidirectionalText = textGenerationWith("transformation-text-two-way", TransformationDirectionality.BIDIRECTIONAL);

        TransformationCheckReport traceReport = env.transformations().check(withoutTrace);
        assertThat(traceReport.valid()).isFalse();
        assertThat(traceReport.diagnostics()).extracting(TransformationDiagnostic::code).contains("BIDIRECTIONAL_TRACE_REQUIRED");
        TransformationCheckReport textReport = env.transformations().check(bidirectionalText);
        assertThat(textReport.valid()).isFalse();
        assertThat(textReport.diagnostics()).extracting(TransformationDiagnostic::code).contains("BIDIRECTIONAL_ONLY_M2M");

        for(TransformationDefinitionDocument rejected : List.of(withoutTrace, bidirectionalText)) {
            assertThatThrownBy(() -> env.transformations().create(rejected))
                    .isInstanceOf(TransformationApplicationException.class)
                    .hasMessage("Transformation contains blocking diagnostics")
                    .extracting(exception -> ((TransformationApplicationException) exception).getReason())
                    .isEqualTo(TransformationApplicationException.Reason.INVALID_REQUEST);
            // Al no existir la definicion, tampoco se puede abrir una sesion sobre ella.
            assertThatThrownBy(() -> env.sessions().create(rejected.id(), sessionRequest()))
                    .isInstanceOf(TransformationApplicationException.class)
                    .extracting(exception -> ((TransformationApplicationException) exception).getReason())
                    .isEqualTo(TransformationApplicationException.Reason.NOT_FOUND);
        }

        assertThat(regularFiles(temporary.resolve("transformations"))).isEmpty();
        assertThat(regularFiles(env.properties().getSynchronizationSessionDirectory())).isEmpty();
        assertThat(regularFiles(env.properties().getSynchronizationHistoryDirectory())).isEmpty();
    }

    @Test
    @DisplayName("x_transformation_run_and_sync.mode: la bidireccional M2M con rastro activo si abre sesion y deja fichero de sesion y punto de control")
    void bidirectionalModelToModelWithTraceOpensASessionPersistedOnDisk(@TempDir Path temporary) throws IOException {
        Env env = Env.open(temporary);
        env.transformations().create(m2m("transformation-two-way", TransformationDirectionality.BIDIRECTIONAL, true, true));

        TransformationSynchronizationSessionMutationResult created = env.sessions().create("transformation-two-way", sessionRequest());

        assertThat(created.advanced()).isTrue();
        assertThat(created.session().revision()).isEqualTo(1);
        assertThat(created.session().transformationId()).isEqualTo("transformation-two-way");
        assertThat(created.session().baselineTrace().links()).hasSize(1);
        List<Path> sessionFiles = regularFiles(env.properties().getSynchronizationSessionDirectory());
        assertThat(sessionFiles).hasSize(1);
        assertThat(sessionFiles.getFirst().getFileName().toString()).startsWith(created.session().id());
        assertThat(Files.readString(sessionFiles.getFirst())).contains("transformation-two-way");
        assertThat(regularFiles(env.properties().getSynchronizationHistoryDirectory())).hasSize(1);
    }

    // ----------------------------------------------------------------------------------- fixtures

    private static List<Path> regularFiles(Path directory) throws IOException {
        if(!Files.isDirectory(directory)) return List.of();
        try(Stream<Path> files = Files.walk(directory)) {
            return files.filter(Files::isRegularFile).toList();
        }
    }

    private static TransformationSynchronizationSessionCreateRequest sessionRequest() {
        CanonicalModelElement alice = new CanonicalModelElement("object:alice", ExpressionTestFixtures.PERSON, Map.of(NAME, "Alice"), Map.of());
        CanonicalModelDocument source = new CanonicalModelDocument("1.0", "people-source", ExpressionTestFixtures.METAMODEL_ID, "etag-1",
                "People source", List.of("object:alice"), List.of(alice), List.of(), Map.of());
        CanonicalModelDocument target = new CanonicalModelDocument("1.0", "people-target-seed", ExpressionTestFixtures.METAMODEL_ID, "etag-1",
                "People target", List.of(), List.of(), List.of(), Map.of());
        return new TransformationSynchronizationSessionCreateRequest(source, target, TransformationSynchronizationSide.SOURCE);
    }

    private static TransformationEndpoint endpoint(String id, TransformationEndpointRole role) {
        return new TransformationEndpoint(id, role, ExpressionTestFixtures.METAMODEL_ID, "", id, true, Map.of());
    }

    private static TransformationDefinitionDocument m2m(String id, TransformationDirectionality directionality, boolean trace, boolean withReverseRule) {
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
        return new TransformationDefinitionDocument("1.1", id, "People " + id, TransformationKind.M2M, directionality,
                new TransformationSynchronizationPolicy(TransformationConflictPolicy.FAIL_ON_CONFLICT, false, true),
                List.of(endpoint("source", TransformationEndpointRole.SOURCE)), List.of(endpoint("target", TransformationEndpointRole.TARGET)),
                withReverseRule ? List.of(forward, reverse) : List.of(forward), List.of(), null, null,
                new TransformationTracePolicy(trace, trace), Map.of());
    }

    private static TransformationDefinitionDocument textGeneration(String id) {
        return textGenerationWith(id, TransformationDirectionality.UNIDIRECTIONAL);
    }

    private static TransformationDefinitionDocument textGenerationWith(String id, TransformationDirectionality directionality) {
        TransformationGenerationRule rule = new TransformationGenerationRule("gen:person", "Generate person", true, "source",
                ExpressionTestFixtures.PERSON, null, "out/{{name}}.txt", "Hello {{name}}", GenerationOverwritePolicy.FAIL, Map.of());
        return new TransformationDefinitionDocument("1.1", id, "Text " + id, TransformationKind.M2T, directionality,
                new TransformationSynchronizationPolicy(TransformationConflictPolicy.FAIL_ON_CONFLICT, false, true),
                List.of(endpoint("source", TransformationEndpointRole.SOURCE)), List.of(), List.of(), List.of(rule), null, null,
                TransformationTracePolicy.defaults(), Map.of());
    }

    private record Env(TransformationSynchronizationSessionService sessions, TransformationApplicationService transformations,
            TransformationProperties properties) {

        static Env open(Path temporary) {
            tools.jackson.databind.json.JsonMapper mapper = tools.jackson.databind.json.JsonMapper.builder().findAndAddModules().build();
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
            metamodels.create(ExpressionTestFixtures.metamodel());
            TransformationRuntimeService runtime = new TransformationRuntimeService(transformations, metamodels,
                    new TransformationPlanCompiler(checker), new CanonicalTransformationRuntime(new ExpressionEvaluator(typeChecker)));
            TransformationSynchronizationSessionService sessions = new TransformationSynchronizationSessionService(
                    sessionRepository, runtime, transformations, 25, CLOCK);
            return new Env(sessions, transformations, properties);
        }
    }
}
