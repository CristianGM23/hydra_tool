package com.example.editor.transformation.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
import com.example.editor.transformation.TransformationApplicationService;
import com.example.editor.transformation.TransformationDefinitionChecker;
import com.example.editor.transformation.TransformationProperties;
import com.example.editor.transformation.core.TransformationBinding;
import com.example.editor.transformation.core.TransformationBindingKind;
import com.example.editor.transformation.core.TransformationDefinitionDocument;
import com.example.editor.transformation.core.TransformationEndpoint;
import com.example.editor.transformation.core.TransformationEndpointRole;
import com.example.editor.transformation.core.TransformationExecutionDirection;
import com.example.editor.transformation.core.TransformationExecutionMode;
import com.example.editor.transformation.core.TransformationExecutionPolicy;
import com.example.editor.transformation.core.TransformationRule;
import com.example.editor.transformation.core.TransformationTargetPattern;
import com.example.editor.transformation.persistence.JsonTransformationRepository;
import com.example.editor.transformation.sync.JsonTransformationSynchronizationSessionRepository;
import com.example.editor.transformation.testing.persistence.JsonTransformationTestCaseRepository;
import com.example.editor.validation.model.CanonicalModelDocument;
import com.example.editor.validation.model.CanonicalModelElement;
import tools.jackson.databind.json.JsonMapper;

/**
 * Evidencia real, sin mocks, de «Elegir modelo de entrada» (transformation_execute.in): el servicio
 * real de runtime recibe una definicion en borrador, el modelo de entrada y la direccion, resuelve
 * los metamodelos guardados de entrada y salida y devuelve un modelo destino concreto. Una
 * definicion con tipo de origen desconocido o con una politica de ejecucion no soportada se
 * rechaza con TransformationExecutionException antes de ejecutar ninguna regla ni persistir nada.
 */
class TransformationRuntimeServiceExecuteDraftIntegrationTests {

    @Test
    @DisplayName("transformation_execute.in: executeDraft FORWARD devuelve el modelo destino con los valores esperados")
    void executeDraftForwardReturnsTheTargetModelOfTheSavedMetamodel(@TempDir Path temporary) throws IOException {
        TransformationRuntimeService service = service(temporary);
        CanonicalModelDocument source = ExpressionTestFixtures.model();

        TransformationExecutionResult result = service.executeDraft(document("transformation-draft-ok",
                ExpressionTestFixtures.PERSON, TransformationExecutionPolicy.defaults()), source, TransformationExecutionDirection.FORWARD);

        CanonicalModelDocument target = result.targetModel();
        assertThat(target.metamodelId()).isEqualTo("library");
        assertThat(target.annotations()).containsEntry("hydra.transformation.direction", "FORWARD")
                .containsEntry("hydra.transformation.sourceModel", "library-model");
        // Alice (Person) y Bob (Employee hereda de Person): dos elementos Person con solo el nombre asignado.
        assertThat(target.elements()).hasSize(2);
        assertThat(target.elements()).allSatisfy(element -> {
            assertThat(element.classId()).isEqualTo("class:Person");
            assertThat(element.attributes()).containsOnlyKeys(ExpressionTestFixtures.NAME);
        });
        assertThat(target.elements()).extracting(element -> element.annotations().get("hydra.transformation.source"))
                .containsExactlyInAnyOrder("object:alice", "object:bob");
        CanonicalModelElement aliceTarget = target.elements().stream()
                .filter(element -> "object:alice".equals(element.annotations().get("hydra.transformation.source"))).findFirst().orElseThrow();
        assertThat(aliceTarget.attributes().get(ExpressionTestFixtures.NAME)).isEqualTo("Alice");
        // La ejecucion de un borrador no modifica el modelo de entrada.
        assertThat(source.elements()).hasSize(3);
        assertThat(source.findElement("object:alice").orElseThrow().attributes().get(ExpressionTestFixtures.NAME)).isEqualTo("Alice");
        // Y no persiste nada: el directorio de definiciones no existe o esta vacio.
        assertThat(filesUnder(temporary.resolve("transformations"))).isEmpty();
    }

    @Test
    @DisplayName("transformation_execute.in: la direccion por defecto es FORWARD y sin modelo la entrada se rechaza")
    void defaultDirectionIsForwardAndMissingInputsAreRejected(@TempDir Path temporary) {
        TransformationRuntimeService service = service(temporary);
        TransformationDefinitionDocument document = document("transformation-draft-default",
                ExpressionTestFixtures.PERSON, TransformationExecutionPolicy.defaults());

        TransformationExecutionResult result = service.executeDraft(document, ExpressionTestFixtures.model());
        assertThat(result.targetModel().annotations()).containsEntry("hydra.transformation.direction", "FORWARD");
        assertThat(result.targetModel().elements()).hasSize(2);

        assertThatThrownBy(() -> service.executeDraft(document, null))
                .isInstanceOf(TransformationExecutionException.class)
                .hasMessage("Transformation draft and source model are required");
    }

    @Test
    @DisplayName("transformation_execute.in: tipo de origen desconocido -> excepcion de compilacion, sin ejecutar ni persistir")
    void unknownSourceTypeIsRejectedBeforeExecutingAnything(@TempDir Path temporary) throws IOException {
        TransformationRuntimeService service = service(temporary);
        TransformationDefinitionDocument document = document("transformation-draft-ghost",
                "class:Ghost", TransformationExecutionPolicy.defaults());

        assertThatThrownBy(() -> service.executeDraft(document, ExpressionTestFixtures.model(), TransformationExecutionDirection.FORWARD))
                .isInstanceOf(TransformationExecutionException.class)
                .hasMessageContaining("cannot be compiled because it contains blocking diagnostics")
                .hasMessageContaining("UNKNOWN_SOURCE_TYPE");
        assertThat(filesUnder(temporary.resolve("transformations"))).isEmpty();
    }

    @Test
    @DisplayName("transformation_execute.in: politica de ejecucion no soportada -> excepcion de compilacion")
    void unsupportedExecutionPolicyIsRejectedBeforeExecutingAnything(@TempDir Path temporary) {
        TransformationRuntimeService service = service(temporary);
        TransformationDefinitionDocument nonDeterministic = document("transformation-draft-nondeterministic",
                ExpressionTestFixtures.PERSON, new TransformationExecutionPolicy(TransformationExecutionMode.BATCH, false, true));
        TransformationDefinitionDocument bestEffort = document("transformation-draft-besteffort",
                ExpressionTestFixtures.PERSON, new TransformationExecutionPolicy(TransformationExecutionMode.BATCH, true, false));

        assertThatThrownBy(() -> service.executeDraft(nonDeterministic, ExpressionTestFixtures.model()))
                .isInstanceOf(TransformationExecutionException.class)
                .hasMessageContaining("NON_DETERMINISTIC_EXECUTION_NOT_SUPPORTED");
        assertThatThrownBy(() -> service.executeDraft(bestEffort, ExpressionTestFixtures.model()))
                .isInstanceOf(TransformationExecutionException.class)
                .hasMessageContaining("BEST_EFFORT_BINDING_EXECUTION_NOT_SUPPORTED");
    }

    private static List<Path> filesUnder(Path directory) throws IOException {
        if(!Files.exists(directory)) return List.of();
        try(Stream<Path> walk = Files.walk(directory)) {
            return walk.filter(Files::isRegularFile).toList();
        }
    }

    private static TransformationDefinitionDocument document(String id, String sourceTypeId, TransformationExecutionPolicy policy) {
        TransformationRule rule = new TransformationRule("rule:person", "Person to Person", true, "source",
                sourceTypeId, "personSource", null,
                List.of(new TransformationTargetPattern("target:person", "target", ExpressionTestFixtures.PERSON, "person", Map.of())),
                List.of(new TransformationBinding("binding:name", "person", ExpressionTestFixtures.NAME, TransformationBindingKind.DIRECT,
                        ExpressionTestFixtures.NAME, null, null, null, Map.of())), Map.of());
        return new TransformationDefinitionDocument("1.0", id, "Draft " + id, null,
                List.of(new TransformationEndpoint("source", TransformationEndpointRole.SOURCE, ExpressionTestFixtures.METAMODEL_ID, "", "source", true, Map.of())),
                List.of(new TransformationEndpoint("target", TransformationEndpointRole.TARGET, ExpressionTestFixtures.METAMODEL_ID, "", "target", true, Map.of())),
                List.of(rule), List.of(), null, policy, null, Map.of());
    }

    private static TransformationRuntimeService service(Path temporary) {
        JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();
        StorageProperties storage = new StorageProperties(temporary.resolve("metamodels"), temporary.resolve("models"),
                temporary.resolve("notations"), temporary.resolve("validations"), temporary.resolve("quickfixes"),
                temporary.resolve("generators"), temporary.resolve("assets"), temporary.resolve("exports"), true);
        CanonicalMetamodelApplicationService metamodels = new CanonicalMetamodelApplicationService(
                new JsonCanonicalMetamodelRepository(storage, new CanonicalMetamodelProperties(), mapper), new NeutralMetamodelValidator());
        metamodels.create(ExpressionTestFixtures.metamodel());

        TransformationProperties properties = new TransformationProperties();
        properties.setDirectory(temporary.resolve("transformations"));
        properties.setTestDirectory(temporary.resolve("transformation-tests"));
        properties.setSynchronizationSessionDirectory(temporary.resolve("transformation-sync-sessions"));
        properties.setSynchronizationHistoryDirectory(temporary.resolve("transformation-sync-history"));

        ExpressionTypeChecker typeChecker = new ExpressionTypeChecker(new ExpressionDependencyAnalyzer());
        TransformationDefinitionChecker checker = new TransformationDefinitionChecker(typeChecker);
        HydraArtifactTransactionService transactions = new HydraArtifactTransactionService(
                temporary.resolve("artifact-transactions").toString(), new HydraFaultInjectionService(new HydraFaultInjectionProperties()));
        TransformationApplicationService transformations = new TransformationApplicationService(
                new JsonTransformationRepository(properties, mapper), metamodels,
                new MetamodelValidationFingerprint(mapper), checker,
                new JsonTransformationTestCaseRepository(properties, mapper, new LocalCanonicalDocumentStoreFactory()),
                new JsonTransformationSynchronizationSessionRepository(properties, mapper, new LocalCanonicalDocumentStoreFactory()),
                transactions);

        TransformationPlanCompiler compiler = new TransformationPlanCompiler(checker);
        CanonicalTransformationRuntime runtime = new CanonicalTransformationRuntime(new ExpressionEvaluator(typeChecker));
        return new TransformationRuntimeService(transformations, metamodels, compiler, runtime);
    }
}
