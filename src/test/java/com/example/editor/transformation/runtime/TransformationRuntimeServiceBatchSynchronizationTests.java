package com.example.editor.transformation.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.editor.artifact.transaction.HydraArtifactTransactionService;
import com.example.editor.config.StorageProperties;
import com.example.editor.metamodel.application.CanonicalMetamodelApplicationService;
import com.example.editor.metamodel.canonical.CanonicalMetamodelProperties;
import com.example.editor.metamodel.canonical.JsonCanonicalMetamodelRepository;
import com.example.editor.metamodel.validation.NeutralMetamodelValidator;
import com.example.editor.metamodel.validation.MetamodelValidationFingerprint;
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
import com.example.editor.transformation.sync.JsonTransformationSynchronizationSessionRepository;
import com.example.editor.transformation.testing.persistence.JsonTransformationTestCaseRepository;
import tools.jackson.databind.json.JsonMapper;

/**
 * Evidencia real, sin mocks, del modo batch (no incremental) de sincronizacion bidireccional que
 * respalda transformation_batch_both: con la politica real «parar ante conflicto» ambos lados
 * cambiados detienen la sincronizacion con un error real en vez de perder cambios en silencio; con
 * «gana el origen» o «gana el destino» el lado ganador reconstruye de verdad el lado opuesto
 * completo usando el mismo runtime canonico real ya probado para la ejecucion M2M.
 */
class TransformationRuntimeServiceBatchSynchronizationTests {

    @Test
    void bothSidesChangedWithFailOnConflictStopsWithARealErrorInsteadOfGuessing(@TempDir Path temporary) {
        TransformationRuntimeService service = service(temporary, TransformationConflictPolicy.FAIL_ON_CONFLICT);
        TransformationDefinitionDocument document = bidirectionalDocument(TransformationConflictPolicy.FAIL_ON_CONFLICT);
        TransformationSynchronizationRequest request = new TransformationSynchronizationRequest(
                ExpressionTestFixtures.model(), ExpressionTestFixtures.model(), TransformationSynchronizationSide.BOTH);

        assertThatThrownBy(() -> service.synchronizeDraft(document, request))
                .isInstanceOf(TransformationExecutionException.class)
                .hasMessageContaining("FAIL_ON_CONFLICT");
    }

    @Test
    void bothSidesChangedWithSourceWinsRebuildsTheWholeTargetFromTheRealSource(@TempDir Path temporary) {
        TransformationRuntimeService service = service(temporary, TransformationConflictPolicy.FORWARD_WINS);
        TransformationDefinitionDocument document = bidirectionalDocument(TransformationConflictPolicy.FORWARD_WINS);
        TransformationSynchronizationRequest request = new TransformationSynchronizationRequest(
                ExpressionTestFixtures.model(), ExpressionTestFixtures.model(), TransformationSynchronizationSide.BOTH);

        TransformationSynchronizationResult result = service.synchronizeDraft(document, request);

        assertThat(result.appliedDirection()).isEqualTo(TransformationExecutionDirection.FORWARD);
        assertThat(result.conflictResolution()).isEqualTo("FORWARD_WINS");
        assertThat(result.targetModel().elements()).hasSize(2);
    }

    @Test
    void bothSidesChangedWithTargetWinsRebuildsTheWholeSourceFromTheRealTarget(@TempDir Path temporary) {
        TransformationRuntimeService service = service(temporary, TransformationConflictPolicy.REVERSE_WINS);
        TransformationDefinitionDocument document = bidirectionalDocument(TransformationConflictPolicy.REVERSE_WINS);
        TransformationSynchronizationRequest request = new TransformationSynchronizationRequest(
                ExpressionTestFixtures.model(), ExpressionTestFixtures.model(), TransformationSynchronizationSide.BOTH);

        TransformationSynchronizationResult result = service.synchronizeDraft(document, request);

        assertThat(result.appliedDirection()).isEqualTo(TransformationExecutionDirection.REVERSE);
        assertThat(result.conflictResolution()).isEqualTo("REVERSE_WINS");
        assertThat(result.sourceModel().elements()).hasSize(2);
    }

    private static TransformationDefinitionDocument bidirectionalDocument(TransformationConflictPolicy policy) {
        TransformationRule forward = new TransformationRule("rule:person", "Person to Person", true, "source",
                ExpressionTestFixtures.PERSON, "personSource", null,
                List.of(new TransformationTargetPattern("target:person", "target", ExpressionTestFixtures.PERSON, "person", Map.of())),
                List.of(new TransformationBinding("binding:name", "person", ExpressionTestFixtures.NAME, TransformationBindingKind.DIRECT,
                        ExpressionTestFixtures.NAME, null, null, null, Map.of())),
                Map.of(), TransformationRuleDirection.FORWARD, "corr:person");
        TransformationRule reverse = new TransformationRule("rule:person-reverse", "Person to Person (reverse)", true, "target",
                ExpressionTestFixtures.PERSON, "personTarget", null,
                List.of(new TransformationTargetPattern("source:person", "source", ExpressionTestFixtures.PERSON, "person", Map.of())),
                List.of(new TransformationBinding("binding:name-reverse", "person", ExpressionTestFixtures.NAME, TransformationBindingKind.DIRECT,
                        ExpressionTestFixtures.NAME, null, null, null, Map.of())),
                Map.of(), TransformationRuleDirection.REVERSE, "corr:person");
        return new TransformationDefinitionDocument("1.1", "transformation-batch-both", "Batch both", null,
                TransformationDirectionality.BIDIRECTIONAL,
                new TransformationSynchronizationPolicy(policy, false, true),
                List.of(new TransformationEndpoint("source", TransformationEndpointRole.SOURCE, ExpressionTestFixtures.METAMODEL_ID, "", "source", true, Map.of())),
                List.of(new TransformationEndpoint("target", TransformationEndpointRole.TARGET, ExpressionTestFixtures.METAMODEL_ID, "", "target", true, Map.of())),
                List.of(forward, reverse), List.of(), null, null, TransformationTracePolicy.defaults(), Map.of());
    }

    private static TransformationRuntimeService service(Path temporary, TransformationConflictPolicy policy) {
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
