package com.example.editor.validation.quickfix;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.editor.config.StorageProperties;
import com.example.editor.emf.DynamicModelService;
import com.example.editor.emf.EmfResourceSetFactory;
import com.example.editor.emf.MetamodelLoader;
import com.example.editor.emf.XmiModelRepository;
import com.example.editor.event.CanonicalEvent;
import com.example.editor.event.bus.InMemoryCanonicalEventBus;
import com.example.editor.metamodel.application.CanonicalMetamodelApplicationService;
import com.example.editor.metamodel.behavior.OperationBehaviorModelApplicationService;
import com.example.editor.metamodel.behavior.OperationBehaviorModelProperties;
import com.example.editor.metamodel.behavior.OperationQueryRuntime;
import com.example.editor.metamodel.behavior.persistence.JsonOperationBehaviorModelRepository;
import com.example.editor.metamodel.canonical.CanonicalMetamodelProperties;
import com.example.editor.metamodel.canonical.JsonCanonicalMetamodelRepository;
import com.example.editor.metamodel.core.MetaAttribute;
import com.example.editor.metamodel.core.MetaClass;
import com.example.editor.metamodel.core.MetaClassKind;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.core.MetaPrimitiveType;
import com.example.editor.metamodel.core.MetaRelation;
import com.example.editor.metamodel.core.MetaRelationKind;
import com.example.editor.metamodel.core.MetaTypeReference;
import com.example.editor.metamodel.core.Multiplicity;
import com.example.editor.metamodel.registry.MetamodelRegistryService;
import com.example.editor.metamodel.registry.RegisteredMetamodel;
import com.example.editor.metamodel.registry.RuntimeMetamodelTestSupport;
import com.example.editor.metamodel.validation.NeutralMetamodelValidator;
import com.example.editor.metamodel.validation.OperationBehaviorModelValidator;
import com.example.editor.model.ModelDocumentResponse;
import com.example.editor.model.ModelDocumentService;
import com.example.editor.semantic.expression.AttributeAccessExpressionNode;
import com.example.editor.semantic.expression.BinaryExpressionNode;
import com.example.editor.semantic.expression.BinaryExpressionOperator;
import com.example.editor.semantic.expression.ExpressionDefinition;
import com.example.editor.semantic.expression.ExpressionDependencyAnalyzer;
import com.example.editor.semantic.expression.ExpressionEvaluator;
import com.example.editor.semantic.expression.ExpressionTypeChecker;
import com.example.editor.semantic.expression.ExpressionValueKind;
import com.example.editor.semantic.expression.ExpressionValueType;
import com.example.editor.semantic.expression.LiteralExpressionNode;
import com.example.editor.semantic.expression.SelfExpressionNode;
import com.example.editor.validation.action.ActionDefinition;
import com.example.editor.validation.action.ActionModel;
import com.example.editor.validation.action.ActionModelApplicationService;
import com.example.editor.validation.action.ActionModelChecker;
import com.example.editor.validation.action.ActionModelProperties;
import com.example.editor.validation.action.ActionSelector;
import com.example.editor.validation.action.ActionSelectorKind;
import com.example.editor.validation.action.ActionTransactionPolicy;
import com.example.editor.validation.action.ActionUndoPolicy;
import com.example.editor.validation.action.ActionValueSource;
import com.example.editor.validation.action.CanonicalActionExecutor;
import com.example.editor.validation.action.SetAttributeOperation;
import com.example.editor.validation.action.persistence.JsonActionModelRepository;
import com.example.editor.validation.expression.ExpressionModel;
import com.example.editor.validation.expression.ExpressionModelApplicationService;
import com.example.editor.validation.expression.ExpressionModelProperties;
import com.example.editor.validation.expression.persistence.JsonExpressionModelRepository;
import com.example.editor.validation.orchestration.DefaultValidationBlockingPolicy;
import com.example.editor.validation.orchestration.LiveValidationRequest;
import com.example.editor.validation.orchestration.ValidationOrchestrationProperties;
import com.example.editor.validation.orchestration.ValidationOrchestrator;
import com.example.editor.validation.runtime.ModelValidationDiagnostic;
import com.example.editor.validation.runtime.ValidationCompiler;
import com.example.editor.validation.runtime.ValidationEngine;
import com.example.editor.validation.runtime.ValidationPlanCache;
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
 * Evidencia real, sin mocks, de que validar y corregir publican sus eventos en el bus real
 * (events_publish_session.a8) y de la decision "hay diagnosticos" con sus correcciones
 * (x_validate_fix_revalidate_reward.d1). Cadena real: modelo EMF vivo, motor de validacion, orquestador,
 * corrección rápida y bus en memoria; el oraculo son los eventos recogidos en el bus con literales esperados.
 */
class ValidationQuickFixEventEmissionRuntimeIntegrationTests {

    private static final String METAMODEL = "introspection";
    private static final String NS_URI = "https://example.com/introspection";
    private static final String MODEL_CLASS_ID = NS_URI + "#//Model";
    private static final String NODE_CLASS_ID = NS_URI + "#//Node";
    private static final String NODE = "class:Node";
    private static final String NAME = "attribute:Node:name";
    private static final String PRIORITY = "attribute:Node:priority";
    private static final String RULE = "rule:name-not-invalid";
    private static final String FIX = "action:reset";

    @TempDir
    Path temporaryDirectory;

    private ValidationOrchestrator orchestrator;
    private QuickFixRuntimeService quickFixes;
    private final List<CanonicalEvent> published = new ArrayList<>();
    private String modelId;
    private String node1;

    @BeforeEach
    void setUp() throws Exception {
        StorageProperties storage = new StorageProperties(temporaryDirectory.resolve("metamodels"), temporaryDirectory.resolve("models"),
                temporaryDirectory.resolve("notations"), temporaryDirectory.resolve("validations"), temporaryDirectory.resolve("quickfixes"),
                temporaryDirectory.resolve("generators"), temporaryDirectory.resolve("assets"), temporaryDirectory.resolve("exports"), true);
        Files.createDirectories(storage.metamodels());
        JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();

        EmfResourceSetFactory resourceSetFactory = new EmfResourceSetFactory();
        MetamodelLoader metamodelLoader = new MetamodelLoader(resourceSetFactory);
        URL resourceUrl = Objects.requireNonNull(getClass().getResource("/metamodels/introspection.ecore"), "Test resource was not found");
        Path ecorePath = storage.metamodels().resolve("introspection.ecore");
        Files.copy(Path.of(resourceUrl.toURI()), ecorePath);
        RegisteredMetamodel registered = RuntimeMetamodelTestSupport.load(METAMODEL, "introspection.ecore", ecorePath, metamodelLoader);
        MetamodelRegistryService registryService = RuntimeMetamodelTestSupport.fixedRegistry(storage, metamodelLoader, List.of(registered));
        ModelDocumentService modelService = new ModelDocumentService(storage, registryService, metamodelLoader, new DynamicModelService(), new XmiModelRepository());

        CanonicalMetamodelApplicationService metamodelService = new CanonicalMetamodelApplicationService(
                new JsonCanonicalMetamodelRepository(storage, new CanonicalMetamodelProperties(), mapper), new NeutralMetamodelValidator());
        metamodelService.create(canonicalMetamodel());

        ExpressionTypeChecker typeChecker = new ExpressionTypeChecker(new ExpressionDependencyAnalyzer());
        ExpressionEvaluator evaluator = new ExpressionEvaluator(typeChecker);
        JsonExpressionModelRepository expressionRepository = new JsonExpressionModelRepository(storage, new ExpressionModelProperties(), mapper);
        ExpressionModelApplicationService expressions = new ExpressionModelApplicationService(expressionRepository, metamodelService, typeChecker, evaluator);
        expressions.create(METAMODEL, expressionModel());
        JsonActionModelRepository actionRepository = new JsonActionModelRepository(storage, new ActionModelProperties(), mapper);
        ActionModelApplicationService actions = new ActionModelApplicationService(actionRepository, expressionRepository, metamodelService,
                new ActionModelChecker(typeChecker));
        actions.create(METAMODEL, actionModel());
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
        bus.subscribe(event -> true, published::add);
        ValidationOrchestrationProperties orchestrationProperties = new ValidationOrchestrationProperties();
        orchestrator = new ValidationOrchestrator(runtime, metamodelService, modelService,
                new DefaultValidationBlockingPolicy(orchestrationProperties), orchestrationProperties, bus);
        quickFixes = new QuickFixRuntimeService(metamodelService, behaviorService, expressionRepository, actionRepository,
                new CanonicalActionExecutor(evaluator, new OperationQueryRuntime(evaluator)), modelService, orchestrator,
                new CanonicalModelChangeAnalyzer(), bus);

        ModelDocumentResponse doc = modelService.create(METAMODEL, MODEL_CLASS_ID);
        modelId = doc.id();
        doc = modelService.addElement(modelId, doc.rootElementId(), "elements", NODE_CLASS_ID);
        node1 = doc.elements().get(1).id();
        modelService.setAttribute(modelId, node1, "name", "INVALID");
        orchestrator.validateLive(modelId, LiveValidationRequest.full());
    }

    @Test
    @DisplayName("events_publish_session.a8: ejecutar la validacion emite el evento de ejecucion completada y el de problema creado")
    void runningTheValidationEmitsTheExecutionAndTheViolationEvents() {
        List<String> types = published.stream().map(CanonicalEvent::eventTypeId).toList();
        assertThat(types).containsExactly("core.validation.execution-completed", "core.validation.violation-created");

        CanonicalEvent completed = published.get(0);
        assertThat(completed.payload().get("diagnosticCount")).isEqualTo(1);
        assertThat(completed.payload().get("createdCount")).isEqualTo(1);
        assertThat(completed.payload().get("resolvedCount")).isEqualTo(0);
        assertThat(completed.payload().get("mode")).isEqualTo("FULL");
        assertThat(completed.context().metamodelId()).isEqualTo(METAMODEL);
        assertThat(completed.context().modelId()).isEqualTo(modelId);

        CanonicalEvent created = published.get(1);
        ModelValidationDiagnostic diagnostic = diagnostic();
        assertThat(created.payload()).containsEntry("diagnosticId", diagnostic.id()).containsEntry("ruleId", RULE)
                .containsEntry("severity", "ERROR").containsEntry("elementId", node1);
    }

    @Test
    @DisplayName("events_publish_session.a8: aplicar una correccion emite revalidacion, problema resuelto y correccion iniciada y aplicada; deshacer emite el de deshecha")
    void applyingAndUndoingAQuickFixEmitsTheFixEvents() {
        String diagnosticId = diagnostic().id();
        published.clear();

        QuickFixApplicationResult applied = quickFixes.apply(modelId, diagnosticId, FIX, new QuickFixExecutionRequest(Map.of(), List.of()));

        assertThat(applied.status()).isEqualTo(QuickFixApplicationStatus.APPLIED_RESOLVED);
        assertThat(published).extracting(CanonicalEvent::eventTypeId).containsExactly("core.validation.execution-completed",
                "core.validation.violation-resolved", "core.quickfix.started", "core.quickfix.applied");
        CanonicalEvent resolved = published.get(1);
        assertThat(resolved.payload()).containsEntry("diagnosticId", diagnosticId).containsEntry("ruleId", RULE);
        CanonicalEvent started = published.get(2);
        assertThat(started.payload()).containsEntry("preview", false).containsEntry("diagnosticId", diagnosticId);
        assertThat(started.subject().elementId()).isEqualTo(FIX);
        CanonicalEvent appliedEvent = published.get(3);
        assertThat(appliedEvent.payload()).containsEntry("status", "APPLIED_RESOLVED").containsEntry("diagnosticId", diagnosticId);
        assertThat(appliedEvent.transactionId()).isEqualTo(started.transactionId()).startsWith("transaction:");

        published.clear();
        QuickFixApplicationResult undone = quickFixes.undo(modelId);

        assertThat(undone.status()).isEqualTo(QuickFixApplicationStatus.UNDONE);
        assertThat(published).filteredOn(event -> event.eventTypeId().equals("core.quickfix.undone")).singleElement().satisfies(event -> {
            assertThat(event.payload()).containsEntry("status", "UNDONE").containsEntry("diagnosticId", diagnosticId);
            assertThat(event.subject().elementId()).isEqualTo(FIX);
        });
    }

    @Test
    @DisplayName("x_validate_fix_revalidate_reward.d1: con diagnostico se listan su gravedad y su correccion; sin diagnostico la consulta se rechaza")
    void diagnosticsCarrySeverityAndFixesAndAResolvedOneIsNoLongerFound() {
        ModelValidationDiagnostic diagnostic = diagnostic();
        assertThat(orchestrator.latest(modelId).orElseThrow().diagnostics()).hasSize(1);
        assertThat(diagnostic.severity()).isEqualTo(ValidationSeverity.ERROR);
        assertThat(diagnostic.targetElementId()).isEqualTo(node1);
        assertThat(diagnostic.quickFixIds()).containsExactly(FIX);
        assertThat(quickFixes.resolve(modelId, diagnostic.id())).singleElement().satisfies(descriptor -> {
            assertThat(descriptor.actionId()).isEqualTo(FIX);
            assertThat(descriptor.name()).isEqualTo("Reset name");
            assertThat(descriptor.enabled()).isTrue();
            assertThat(descriptor.transactionPolicy()).isEqualTo(ActionTransactionPolicy.ATOMIC);
            assertThat(descriptor.undoPolicy()).isEqualTo(ActionUndoPolicy.REQUIRED);
        });

        quickFixes.apply(modelId, diagnostic.id(), FIX, new QuickFixExecutionRequest(Map.of(), List.of()));

        assertThat(orchestrator.latest(modelId).orElseThrow().diagnostics()).isEmpty();
        assertThatThrownBy(() -> quickFixes.resolve(modelId, diagnostic.id()))
                .isInstanceOfSatisfying(QuickFixRuntimeException.class,
                        exception -> assertThat(exception.getReason()).isEqualTo(QuickFixRuntimeException.Reason.NOT_FOUND));
    }

    private ModelValidationDiagnostic diagnostic() {
        return orchestrator.latest(modelId).orElseThrow().diagnostics().stream()
                .filter(item -> item.ruleId().equals(RULE) && item.targetElementId().equals(node1)).findFirst().orElseThrow();
    }

    private static MetaModel canonicalMetamodel() {
        MetaAttribute name = new MetaAttribute(NAME, "name", MetaTypeReference.primitive(MetaPrimitiveType.STRING), Multiplicity.optional(), true, true, "", Map.of());
        MetaAttribute priority = new MetaAttribute(PRIORITY, "priority", MetaTypeReference.primitive(MetaPrimitiveType.INTEGER), Multiplicity.optional(), true, true, "", Map.of());
        MetaClass node = new MetaClass(NODE, "Node", MetaClassKind.CLASS, List.of(name, priority), List.of(), Map.of());
        MetaClass model = new MetaClass("class:Model", "Model", MetaClassKind.CLASS, List.of(), List.of(), Map.of());
        MetaRelation elements = new MetaRelation("relation:Model:elements", "elements", "class:Model", NODE, MetaRelationKind.CONTAINMENT,
                Multiplicity.one(), Multiplicity.many(), "", true, true, Map.of());
        return new MetaModel(MetaModel.CURRENT_SCHEMA_VERSION, METAMODEL, "Introspection", List.of(model, node), List.of(), List.of(), List.of(elements), Map.of());
    }

    private static ExpressionModel expressionModel() {
        ExpressionDefinition notInvalid = new ExpressionDefinition("expression:name-not-invalid", "expression:name-not-invalid", "", NODE,
                ExpressionValueType.scalar(ExpressionValueKind.BOOLEAN),
                new BinaryExpressionNode("node:cmp", BinaryExpressionOperator.NOT_EQUALS,
                        new AttributeAccessExpressionNode("node:name", new SelfExpressionNode("node:self"), NAME),
                        new LiteralExpressionNode("node:lit", ExpressionValueType.scalar(ExpressionValueKind.STRING), "INVALID")), Map.of());
        return new ExpressionModel("1.0", METAMODEL, METAMODEL, List.of(notInvalid), Map.of());
    }

    private static ActionModel actionModel() {
        ActionDefinition reset = new ActionDefinition(FIX, "Reset name", "", true, List.of(), "",
                List.of(new SetAttributeOperation("operation:reset", new ActionSelector(ActionSelectorKind.SELF, "", ""), NAME,
                        ActionValueSource.literal("fixed"))), ActionTransactionPolicy.ATOMIC, ActionUndoPolicy.REQUIRED, Map.of());
        return new ActionModel("1.0", METAMODEL, METAMODEL, METAMODEL, List.of(reset), Map.of());
    }

    private static ValidationModel validationModel() {
        ValidationRule rule = new ValidationRule(RULE, "NAME_NOT_INVALID", "NAME_NOT_INVALID", "", true,
                new ValidationContext(ValidationContextKind.ATTRIBUTE, NODE, NAME), ValidationSeverity.ERROR, "expression:name-not-invalid",
                "${elementId} has a bad name", List.of(ValidationTrigger.FULL_VALIDATION, ValidationTrigger.ON_ATTRIBUTE_CHANGE),
                List.of(new ValidationDependency(ValidationDependencyKind.ATTRIBUTE, NAME)), List.of(FIX), ValidationExecutionMode.INCREMENTAL,
                AuthoritativeRepresentation.CANONICAL, ConversionStatus.FULL, null, Map.of());
        return new ValidationModel("1.0", METAMODEL, METAMODEL, METAMODEL, METAMODEL,
                List.of(new ValidationRuleSet("set:default", "Default", "", true, 0, List.of(rule.id()), Map.of())), List.of(rule), List.of(), Map.of());
    }
}
