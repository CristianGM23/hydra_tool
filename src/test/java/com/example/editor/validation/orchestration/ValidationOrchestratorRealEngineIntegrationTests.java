package com.example.editor.validation.orchestration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.editor.config.StorageProperties;
import com.example.editor.event.bus.InMemoryCanonicalEventBus;
import com.example.editor.metamodel.application.CanonicalMetamodelApplicationService;
import com.example.editor.metamodel.behavior.OperationBehaviorModelApplicationService;
import com.example.editor.metamodel.behavior.OperationBehaviorModelProperties;
import com.example.editor.metamodel.behavior.OperationQueryRuntime;
import com.example.editor.metamodel.behavior.persistence.JsonOperationBehaviorModelRepository;
import com.example.editor.metamodel.canonical.CanonicalMetamodelProperties;
import com.example.editor.metamodel.canonical.JsonCanonicalMetamodelRepository;
import com.example.editor.metamodel.validation.NeutralMetamodelValidator;
import com.example.editor.metamodel.validation.OperationBehaviorModelValidator;
import com.example.editor.semantic.expression.ExpressionDependencyAnalyzer;
import com.example.editor.semantic.expression.ExpressionEvaluator;
import com.example.editor.semantic.expression.ExpressionTestFixtures;
import com.example.editor.semantic.expression.ExpressionTypeChecker;
import com.example.editor.validation.action.ActionModel;
import com.example.editor.validation.action.ActionModelApplicationService;
import com.example.editor.validation.action.ActionModelChecker;
import com.example.editor.validation.action.ActionModelProperties;
import com.example.editor.validation.action.persistence.JsonActionModelRepository;
import com.example.editor.validation.expression.ExpressionModelApplicationService;
import com.example.editor.validation.expression.ExpressionModelProperties;
import com.example.editor.validation.expression.persistence.JsonExpressionModelRepository;
import com.example.editor.validation.model.CanonicalModelDocument;
import com.example.editor.validation.model.CanonicalModelElement;
import com.example.editor.validation.runtime.ModelValidationDiagnostic;
import com.example.editor.validation.runtime.ValidationChangeSet;
import com.example.editor.validation.runtime.ValidationCompiler;
import com.example.editor.validation.runtime.ValidationEngine;
import com.example.editor.validation.runtime.ValidationExecutionRequest;
import com.example.editor.validation.runtime.ValidationPlanCache;
import com.example.editor.validation.runtime.ValidationRunMode;
import com.example.editor.validation.runtime.ValidationRuntimeApplicationService;
import com.example.editor.validation.runtime.ValidationRuntimeProperties;
import com.example.editor.validation.validation.AuthoritativeRepresentation;
import com.example.editor.validation.validation.ConversionStatus;
import com.example.editor.validation.validation.ValidationContext;
import com.example.editor.validation.validation.ValidationContextKind;
import com.example.editor.validation.validation.ValidationDependency;
import com.example.editor.validation.validation.ValidationDependencyKind;
import com.example.editor.validation.validation.ValidationExecutionMode;
import com.example.editor.validation.validation.ValidationModel;
import com.example.editor.validation.validation.ValidationModelApplicationService;
import com.example.editor.validation.validation.ValidationModelChecker;
import com.example.editor.validation.validation.ValidationModelProperties;
import com.example.editor.validation.validation.ValidationRule;
import com.example.editor.validation.validation.ValidationRuleSet;
import com.example.editor.validation.validation.ValidationSeverity;
import com.example.editor.validation.validation.ValidationTrigger;
import com.example.editor.validation.validation.persistence.JsonValidationModelRepository;
import tools.jackson.databind.json.JsonMapper;

/**
 * Evidencia real, sin mocks, del orquestador de validacion sobre el motor real:
 * validation_execute.cons (consolidacion de informes parciales), validation_execute.ev (eventos
 * publicados en un bus real con suscriptor) y validation_execute.blk (politica de bloqueo por puerta
 * NONE / SAVE / EXPORT). Metamodelo, expresion y regla se persisten de verdad en @TempDir y el
 * ValidationRuntimeApplicationService las relee del disco en cada ejecucion.
 */
class ValidationOrchestratorRealEngineIntegrationTests {

    private static final String METAMODEL = ExpressionTestFixtures.METAMODEL_ID;
    private static final String RULE = "rule:positive-age";

    @TempDir
    Path temporary;

    private ValidationOrchestrationProperties properties;
    private ValidationOrchestrator orchestrator;
    private final List<String> eventTypes = new ArrayList<>();

    @BeforeEach
    void setUp() {
        JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();
        StorageProperties storage = new StorageProperties(temporary.resolve("metamodels"), temporary.resolve("models"),
                temporary.resolve("notations"), temporary.resolve("validations"), temporary.resolve("quickfixes"),
                temporary.resolve("generators"), temporary.resolve("assets"), temporary.resolve("exports"), true);
        CanonicalMetamodelApplicationService metamodelService = new CanonicalMetamodelApplicationService(
                new JsonCanonicalMetamodelRepository(storage, new CanonicalMetamodelProperties(), mapper), new NeutralMetamodelValidator());
        metamodelService.create(ExpressionTestFixtures.metamodel());
        ExpressionTypeChecker typeChecker = new ExpressionTypeChecker(new ExpressionDependencyAnalyzer());
        ExpressionEvaluator evaluator = new ExpressionEvaluator(typeChecker);
        JsonExpressionModelRepository expressionRepository = new JsonExpressionModelRepository(storage, new ExpressionModelProperties(), mapper);
        ExpressionModelApplicationService expressions = new ExpressionModelApplicationService(expressionRepository, metamodelService, typeChecker, evaluator);
        expressions.create(METAMODEL, ExpressionTestFixtures.expressionModel(ExpressionTestFixtures.positiveAge()));
        JsonActionModelRepository actionRepository = new JsonActionModelRepository(storage, new ActionModelProperties(), mapper);
        ActionModelApplicationService actions = new ActionModelApplicationService(actionRepository, expressionRepository, metamodelService,
                new ActionModelChecker(typeChecker));
        actions.create(METAMODEL, new ActionModel("1.0", METAMODEL, METAMODEL, METAMODEL, List.of(), Map.of()));
        ValidationModelApplicationService validations = new ValidationModelApplicationService(
                new JsonValidationModelRepository(storage, new ValidationModelProperties(), mapper), expressionRepository, actionRepository,
                metamodelService, new ValidationModelChecker(typeChecker));
        validations.create(METAMODEL, validationModel());
        JsonOperationBehaviorModelRepository behaviorRepository = new JsonOperationBehaviorModelRepository(storage,
                new OperationBehaviorModelProperties(), mapper);
        OperationBehaviorModelApplicationService behaviorService = new OperationBehaviorModelApplicationService(behaviorRepository,
                metamodelService, expressionRepository, actionRepository, new OperationBehaviorModelValidator());
        ValidationRuntimeApplicationService runtime = new ValidationRuntimeApplicationService(metamodelService, expressions, actions,
                validations, behaviorService, behaviorRepository, new ValidationCompiler(new ValidationModelChecker(typeChecker),
                        new ValidationPlanCache()), new ValidationEngine(evaluator, new OperationQueryRuntime(evaluator)), null,
                new ValidationRuntimeProperties());
        InMemoryCanonicalEventBus bus = new InMemoryCanonicalEventBus();
        bus.subscribe(event -> true, event -> eventTypes.add(event.eventTypeId()));
        properties = new ValidationOrchestrationProperties();
        orchestrator = new ValidationOrchestrator(runtime, metamodelService, null, new DefaultValidationBlockingPolicy(properties), properties, bus);
    }

    /** cons + ev: el informe completo, la edicion del modelo y la revalidacion INCREMENTAL consolidan y publican. */
    @Test
    void fullThenIncrementalRevalidationConsolidatesAndPublishesEvents() {
        OrchestratedValidationResult first = orchestrator.validate(METAMODEL, request(ValidationRunMode.FULL, -2, 35, ValidationChangeSet.empty(), List.of()),
                ValidationGate.NONE);
        assertThat(first.report().diagnostics()).extracting(ModelValidationDiagnostic::targetElementId).containsExactly("object:bob");
        assertThat(first.delta().created()).hasSize(1);
        assertThat(eventTypes).containsExactly("core.validation.execution-completed", "core.validation.violation-created");
        String bobDiagnostic = first.report().diagnostics().getFirst().id();

        eventTypes.clear();
        ValidationChangeSet change = new ValidationChangeSet(List.of("object:bob"), List.of(), List.of(ExpressionTestFixtures.AGE), List.of(), List.of(), false);
        OrchestratedValidationResult second = orchestrator.validate(METAMODEL, request(ValidationRunMode.INCREMENTAL, 7, 35, change, List.of()),
                ValidationGate.NONE);
        assertThat(second.report().mode()).isEqualTo(ValidationRunMode.INCREMENTAL);
        assertThat(second.report().diagnostics()).isEmpty();
        assertThat(second.delta().resolved()).extracting(ModelValidationDiagnostic::id).containsExactly(bobDiagnostic);
        assertThat(eventTypes).containsExactly("core.validation.execution-completed", "core.validation.violation-resolved");
        assertThat(orchestrator.latest("library-model").orElseThrow().diagnostics()).isEmpty();
    }

    /** cons: SELECTION conserva el problema fuera de la seleccion y descarta el de la seleccion ya corregido. */
    @Test
    void selectionRevalidationKeepsProblemsOutsideTheSelection() {
        OrchestratedValidationResult first = orchestrator.validate(METAMODEL, request(ValidationRunMode.FULL, -2, -1, ValidationChangeSet.empty(), List.of()),
                ValidationGate.NONE);
        assertThat(first.report().diagnostics()).extracting(ModelValidationDiagnostic::targetElementId)
                .containsExactlyInAnyOrder("object:alice", "object:bob");

        OrchestratedValidationResult second = orchestrator.validate(METAMODEL,
                request(ValidationRunMode.SELECTION, 7, -1, ValidationChangeSet.empty(), List.of("object:bob")), ValidationGate.NONE);
        assertThat(second.report().diagnostics()).extracting(ModelValidationDiagnostic::targetElementId).containsExactly("object:alice");
        assertThat(second.delta().resolved()).extracting(ModelValidationDiagnostic::targetElementId).containsExactly("object:bob");
        assertThat(second.delta().unchanged()).extracting(ModelValidationDiagnostic::targetElementId).containsExactly("object:alice");
    }

    /** blk: NONE nunca bloquea; SAVE y EXPORT bloquean con errores, y la configuracion desactiva EXPORT. */
    @Test
    void blockingPolicyDependsOnTheGateAndTheConfiguration() {
        OrchestratedValidationResult none = orchestrator.validate(METAMODEL, request(ValidationRunMode.FULL, -2, 35, ValidationChangeSet.empty(), List.of()),
                ValidationGate.NONE);
        assertThat(none.blocking().blocked()).isFalse();
        assertThat(none.blocking().gate()).isEqualTo(ValidationGate.NONE);

        OrchestratedValidationResult save = orchestrator.validate(METAMODEL, request(ValidationRunMode.FULL, -2, 35, ValidationChangeSet.empty(), List.of()),
                ValidationGate.SAVE);
        assertThat(save.blocking().blocked()).isTrue();
        assertThat(save.blocking().diagnosticIds()).containsExactly(save.report().diagnostics().getFirst().id());
        assertThat(save.report().diagnostics()).hasSize(1);

        OrchestratedValidationResult export = orchestrator.validate(METAMODEL, request(ValidationRunMode.FULL, -2, 35, ValidationChangeSet.empty(), List.of()),
                ValidationGate.EXPORT);
        assertThat(export.blocking().blocked()).isTrue();

        properties.setBlockExportOnErrors(false);
        OrchestratedValidationResult exportAllowed = orchestrator.validate(METAMODEL,
                request(ValidationRunMode.FULL, -2, 35, ValidationChangeSet.empty(), List.of()), ValidationGate.EXPORT);
        assertThat(exportAllowed.blocking().blocked()).isFalse();
        // el bloqueo no altera los diagnosticos: el problema sigue ahi.
        assertThat(exportAllowed.report().diagnostics()).hasSize(1);

        OrchestratedValidationResult fixed = orchestrator.validate(METAMODEL, request(ValidationRunMode.FULL, 7, 35, ValidationChangeSet.empty(), List.of()),
                ValidationGate.SAVE);
        assertThat(fixed.report().diagnostics()).isEmpty();
        assertThat(fixed.blocking().blocked()).isFalse();
    }

    private static ValidationExecutionRequest request(ValidationRunMode mode, int bobAge, int aliceAge, ValidationChangeSet changes, List<String> selection) {
        CanonicalModelDocument base = ExpressionTestFixtures.model();
        CanonicalModelElement company = base.elements().get(0);
        CanonicalModelElement alice = new CanonicalModelElement("object:alice", ExpressionTestFixtures.PERSON,
                Map.of(ExpressionTestFixtures.AGE, aliceAge, ExpressionTestFixtures.NAME, "Alice"), Map.of());
        CanonicalModelElement bob = new CanonicalModelElement("object:bob", ExpressionTestFixtures.EMPLOYEE,
                Map.of(ExpressionTestFixtures.AGE, bobAge, ExpressionTestFixtures.NAME, "Bob"), Map.of());
        CanonicalModelDocument model = new CanonicalModelDocument(base.schemaVersion(), base.id(), base.metamodelId(), base.metamodelEtag(),
                base.name(), base.rootElementIds(), List.of(company, alice, bob), base.relations(), base.annotations());
        return new ValidationExecutionRequest(mode, model, selection, "", changes, Map.of());
    }

    private static ValidationModel validationModel() {
        ValidationRule rule = new ValidationRule(RULE, "Positive age", "POSITIVE_AGE", "", true,
                new ValidationContext(ValidationContextKind.ATTRIBUTE, ExpressionTestFixtures.PERSON, ExpressionTestFixtures.AGE), ValidationSeverity.ERROR,
                ExpressionTestFixtures.positiveAge().id(), "Age must be positive",
                List.of(ValidationTrigger.ON_ATTRIBUTE_CHANGE, ValidationTrigger.ON_SAVE, ValidationTrigger.FULL_VALIDATION),
                List.of(new ValidationDependency(ValidationDependencyKind.CLASS, ExpressionTestFixtures.PERSON),
                        new ValidationDependency(ValidationDependencyKind.ATTRIBUTE, ExpressionTestFixtures.AGE)),
                List.of(), ValidationExecutionMode.INCREMENTAL, AuthoritativeRepresentation.CANONICAL, ConversionStatus.FULL, null, Map.of());
        return new ValidationModel("1.0", METAMODEL, METAMODEL, METAMODEL, METAMODEL,
                List.of(new ValidationRuleSet("set:default", "Default", "", true, 0, List.of(rule.id()), Map.of())), List.of(rule), List.of(), Map.of());
    }
}
