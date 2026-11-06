package com.example.editor.validation.quickfix;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.editor.config.StorageProperties;
import com.example.editor.emf.DynamicModelService;
import com.example.editor.emf.EmfResourceSetFactory;
import com.example.editor.emf.MetamodelLoader;
import com.example.editor.emf.XmiModelRepository;
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
import com.example.editor.validation.action.ActionParameter;
import com.example.editor.validation.action.ActionParameterType;
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
import com.example.editor.validation.orchestration.OrchestratedValidationResult;
import com.example.editor.validation.orchestration.ValidationOrchestrationProperties;
import com.example.editor.validation.orchestration.ValidationOrchestrator;
import com.example.editor.validation.runtime.ModelValidationDiagnostic;
import com.example.editor.validation.runtime.ValidationCompiler;
import com.example.editor.validation.runtime.ValidationEngine;
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
 * Evidencia real y sin mocks del ciclo completo de una Quick Fix sobre una sesion EMF viva:
 * validation_apply_quickfix.{list,par,pv,ap,rb,rv,st,hist,evq,undo} y
 * x_validate_fix_revalidate_reward.{pick,pv,ap,rv,evt,undo}.
 *
 * Cadena real: ModelDocumentService (introspection.ecore), repositorios JSON en @TempDir,
 * ValidationRuntimeApplicationService + ValidationOrchestrator reales y bus de eventos real con
 * suscriptor. El oraculo del modelo es el XMI escrito en disco, leido directamente.
 */
class QuickFixApplyUndoRedoRuntimeIntegrationTests {

    private static final String METAMODEL = "introspection";
    private static final String NS_URI = "https://example.com/introspection";
    private static final String MODEL_CLASS_ID = NS_URI + "#//Model";
    private static final String NODE_CLASS_ID = NS_URI + "#//Node";
    private static final String NODE = "class:Node";
    private static final String NAME = "attribute:Node:name";
    private static final String PRIORITY = "attribute:Node:priority";
    private static final String RULE_A = "rule:name-not-invalid";
    private static final String RULE_B = "rule:name-not-bad";

    @TempDir
    Path temporaryDirectory;

    private StorageProperties storage;
    private ModelDocumentService modelService;
    private ValidationOrchestrationProperties orchestrationProperties;
    private ValidationOrchestrator orchestrator;
    private QuickFixRuntimeService service;
    private final List<String> eventTypes = new ArrayList<>();
    private String modelId;
    private String node1;
    private String node2;

    @BeforeEach
    void setUp() throws Exception {
        storage = new StorageProperties(temporaryDirectory.resolve("metamodels"), temporaryDirectory.resolve("models"),
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
        modelService = new ModelDocumentService(storage, registryService, metamodelLoader, new DynamicModelService(), new XmiModelRepository());

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
        bus.subscribe(event -> true, event -> eventTypes.add(event.eventTypeId()));
        orchestrationProperties = new ValidationOrchestrationProperties();
        orchestrator = new ValidationOrchestrator(runtime, metamodelService, modelService,
                new DefaultValidationBlockingPolicy(orchestrationProperties), orchestrationProperties, bus);
        service = new QuickFixRuntimeService(metamodelService, behaviorService, expressionRepository, actionRepository,
                new CanonicalActionExecutor(evaluator, new OperationQueryRuntime(evaluator)), modelService, orchestrator,
                new CanonicalModelChangeAnalyzer(), bus);

        ModelDocumentResponse doc = modelService.create(METAMODEL, MODEL_CLASS_ID);
        modelId = doc.id();
        doc = modelService.addElement(modelId, doc.rootElementId(), "elements", NODE_CLASS_ID);
        node1 = doc.elements().get(1).id();
        doc = modelService.addElement(modelId, doc.rootElementId(), "elements", NODE_CLASS_ID);
        node2 = doc.elements().get(2).id();
        modelService.setAttribute(modelId, node1, "name", "INVALID");
        modelService.setAttribute(modelId, node2, "name", "BAD");
    }

    /** list / pick: resolve sin informe previo lanza una validacion completa y lista las correcciones reales. */
    @Test
    void resolveWithoutPriorReportRunsFullValidationAndListsRealFixes() {
        assertThat(orchestrator.latest(modelId)).isEmpty();
        assertThatThrownBy(() -> service.resolve(modelId, "diagnostic-unknown"))
                .isInstanceOf(QuickFixRuntimeException.class)
                .extracting(e -> ((QuickFixRuntimeException) e).getReason()).isEqualTo(QuickFixRuntimeException.Reason.NOT_FOUND);
        // La validacion completa se ejecuto de verdad al no haber informe previo.
        assertThat(orchestrator.latest(modelId)).isPresent();
        assertThat(orchestrator.latest(modelId).get().mode()).isEqualTo(ValidationRunMode.FULL);

        String diagnosticId = diagnosticOf(RULE_A, node1).id();
        List<QuickFixDescriptor> fixes = service.resolve(modelId, diagnosticId);
        assertThat(fixes).extracting(QuickFixDescriptor::actionId).containsExactlyInAnyOrder("action:reset", "action:rename-to",
                "action:noop", "action:touch-priority", "action:no-undo", "action:guarded");
        assertThat(service.resolve(modelId, diagnosticOf(RULE_B, node2).id())).isEmpty();
    }

    /** pv + ap + rv + st + hist + evq + evt: vista previa intacta y aplicacion revalidada de forma incremental. */
    @Test
    void previewKeepsModelIntactAndApplyRevalidatesIncrementally() throws Exception {
        orchestrator.validateLive(modelId, LiveValidationRequest.full());
        String diagnosticId = diagnosticOf(RULE_A, node1).id();
        String ruleBDiagnosticId = diagnosticOf(RULE_B, node2).id();
        eventTypes.clear();

        QuickFixPreviewResult preview = service.preview(modelId, diagnosticId, "action:reset", new QuickFixExecutionRequest(Map.of(), List.of()));
        assertThat(preview.previewDocument().elements()).filteredOn(e -> e.id().equals(node1)).singleElement()
                .satisfies(e -> assertThat(e.attributes()).containsEntry(NAME, "fixed"));
        assertThat(preview.changes().attributeIds()).containsExactly(NAME);
        assertThat(onDisk()).contains("INVALID").doesNotContain("fixed");
        assertThat(eventTypes).noneMatch(type -> type.startsWith("core.quickfix."));

        QuickFixApplicationResult result = service.apply(modelId, diagnosticId, "action:reset", new QuickFixExecutionRequest(Map.of(), List.of()));

        assertThat(onDisk()).contains("fixed").doesNotContain("INVALID");
        assertThat(result.status()).isEqualTo(QuickFixApplicationStatus.APPLIED_RESOLVED);
        assertThat(result.diagnosticResolved()).isTrue();
        assertThat(result.validation().report().mode()).isEqualTo(ValidationRunMode.INCREMENTAL);
        assertThat(result.validation().report().diagnostics()).extracting(ModelValidationDiagnostic::id).containsExactly(ruleBDiagnosticId);
        assertThat(result.validation().delta().resolved()).extracting(ModelValidationDiagnostic::id).containsExactly(diagnosticId);
        assertThat(result.validation().delta().created()).isEmpty();
        assertThat(result.undoAvailable()).isTrue();
        assertThat(result.redoAvailable()).isFalse();
        assertThat(eventTypes).contains("core.validation.execution-completed", "core.validation.violation-resolved");
        assertThat(eventTypes.stream().filter(type -> type.startsWith("core.quickfix.")).toList())
                .containsExactly("core.quickfix.started", "core.quickfix.applied");
    }

    /** undo / redo revalidan, reflejan hasUndo/hasRedo, y un apply nuevo vacia redo. */
    @Test
    void undoRedoRevalidateAndANewApplyClearsRedo() throws Exception {
        orchestrator.validateLive(modelId, LiveValidationRequest.full());
        String diagnosticId = diagnosticOf(RULE_A, node1).id();
        service.apply(modelId, diagnosticId, "action:reset", new QuickFixExecutionRequest(Map.of(), List.of()));

        QuickFixApplicationResult undone = service.undo(modelId);
        assertThat(undone.status()).isEqualTo(QuickFixApplicationStatus.UNDONE);
        assertThat(onDisk()).contains("INVALID").doesNotContain("fixed");
        assertThat(undone.validation().report().diagnostics()).extracting(ModelValidationDiagnostic::id).contains(diagnosticId);
        assertThat(undone.diagnosticResolved()).isFalse();
        assertThat(undone.undoAvailable()).isFalse();
        assertThat(undone.redoAvailable()).isTrue();

        QuickFixApplicationResult redone = service.redo(modelId);
        assertThat(redone.status()).isEqualTo(QuickFixApplicationStatus.REDONE);
        assertThat(onDisk()).contains("fixed").doesNotContain("INVALID");
        assertThat(redone.diagnosticResolved()).isTrue();
        assertThat(redone.undoAvailable()).isTrue();
        assertThat(redone.redoAvailable()).isFalse();

        service.undo(modelId);
        QuickFixApplicationResult renamed = service.apply(modelId, diagnosticId, "action:rename-to",
                new QuickFixExecutionRequest(Map.of("newName", "Renamed"), List.of()));
        assertThat(renamed.redoAvailable()).isFalse();
        assertThat(renamed.undoAvailable()).isTrue();
        assertThat(onDisk()).contains("Renamed");
        assertThatThrownBy(() -> service.redo(modelId)).isInstanceOf(QuickFixRuntimeException.class)
                .extracting(e -> ((QuickFixRuntimeException) e).getReason()).isEqualTo(QuickFixRuntimeException.Reason.REDO_NOT_AVAILABLE);
        assertThat(eventTypes).contains("core.quickfix.undone", "core.quickfix.redone");
    }

    /** par: argumentos desconocidos o faltantes se rechazan, el modelo queda identico y se publica failed. */
    @Test
    void unknownOrMissingArgumentsAreRejectedAndTheModelIsUntouched() throws Exception {
        orchestrator.validateLive(modelId, LiveValidationRequest.full());
        String diagnosticId = diagnosticOf(RULE_A, node1).id();
        byte[] before = savedBytes();
        eventTypes.clear();

        assertThatThrownBy(() -> service.apply(modelId, diagnosticId, "action:rename-to",
                new QuickFixExecutionRequest(Map.of("newName", "X", "bogus", "1"), List.of())))
                .isInstanceOf(QuickFixRuntimeException.class).hasMessageContaining("Unknown action parameter: bogus");
        assertThatThrownBy(() -> service.apply(modelId, diagnosticId, "action:rename-to", new QuickFixExecutionRequest(Map.of(), List.of())))
                .isInstanceOf(QuickFixRuntimeException.class).hasMessageContaining("Required action parameter is missing: newName");

        assertThat(savedBytes()).isEqualTo(before);
        assertThat(eventTypes.stream().filter(type -> type.startsWith("core.quickfix.")).toList())
                .containsExactly("core.quickfix.failed", "core.quickfix.failed");
    }

    /** rb: una precondicion falsa aborta y deja el modelo identico. */
    @Test
    void falsePreconditionAbortsAndLeavesTheModelIdentical() throws Exception {
        orchestrator.validateLive(modelId, LiveValidationRequest.full());
        String diagnosticId = diagnosticOf(RULE_A, node1).id();
        byte[] before = savedBytes();
        eventTypes.clear();

        assertThatThrownBy(() -> service.apply(modelId, diagnosticId, "action:guarded", new QuickFixExecutionRequest(Map.of(), List.of())))
                .isInstanceOf(QuickFixRuntimeException.class)
                .satisfies(e -> assertThat(((QuickFixRuntimeException) e).getReason()).isEqualTo(QuickFixRuntimeException.Reason.EXECUTION_FAILED))
                .hasMessageContaining("precondition is not satisfied");

        assertThat(savedBytes()).isEqualTo(before);
        assertThat(eventTypes).contains("core.quickfix.failed").doesNotContain("core.quickfix.applied");
    }

    /**
     * rb: la accion SI modifica el modelo vivo, pero la revalidacion falla (orquestacion desactivada);
     * el servicio restaura la instantanea y el XMI vuelve a ser byte a byte el anterior.
     */
    @Test
    void failureAfterTheModelWasMutatedRollsTheSessionBackToTheIdenticalXmi() throws Exception {
        orchestrator.validateLive(modelId, LiveValidationRequest.full());
        String diagnosticId = diagnosticOf(RULE_A, node1).id();
        byte[] before = savedBytes();
        eventTypes.clear();
        orchestrationProperties.setEnabled(false);

        assertThatThrownBy(() -> service.apply(modelId, diagnosticId, "action:reset", new QuickFixExecutionRequest(Map.of(), List.of())))
                .isInstanceOf(QuickFixRuntimeException.class)
                .satisfies(e -> assertThat(((QuickFixRuntimeException) e).getReason()).isEqualTo(QuickFixRuntimeException.Reason.EXECUTION_FAILED));

        assertThat(savedBytes()).isEqualTo(before);
        assertThat(new String(before, StandardCharsets.UTF_8)).contains("INVALID");
        assertThat(eventTypes).contains("core.quickfix.failed").doesNotContain("core.quickfix.applied");
        assertThatThrownBy(() -> service.undo(modelId)).isInstanceOf(QuickFixRuntimeException.class)
                .extracting(e -> ((QuickFixRuntimeException) e).getReason()).isEqualTo(QuickFixRuntimeException.Reason.UNDO_NOT_AVAILABLE);
    }

    /** st + hist: NO_CHANGES, APPLIED_UNRESOLVED y una accion sin soporte de deshacer no guarda historial. */
    @Test
    void statusIsComputedAndUndoPolicyNotSupportedKeepsNoHistory() throws Exception {
        orchestrator.validateLive(modelId, LiveValidationRequest.full());
        String diagnosticId = diagnosticOf(RULE_A, node1).id();

        QuickFixApplicationResult noChanges = service.apply(modelId, diagnosticId, "action:noop", new QuickFixExecutionRequest(Map.of(), List.of()));
        assertThat(noChanges.status()).isEqualTo(QuickFixApplicationStatus.NO_CHANGES);
        assertThat(noChanges.diagnosticResolved()).isFalse();
        assertThat(noChanges.undoAvailable()).isFalse();

        QuickFixApplicationResult unresolved = service.apply(modelId, diagnosticId, "action:touch-priority",
                new QuickFixExecutionRequest(Map.of(), List.of()));
        assertThat(unresolved.status()).isEqualTo(QuickFixApplicationStatus.APPLIED_UNRESOLVED);
        assertThat(unresolved.diagnosticResolved()).isFalse();
        assertThat(unresolved.messages()).anyMatch(message -> message.contains("still present"));
        assertThat(unresolved.undoAvailable()).isTrue();
        service.undo(modelId);

        QuickFixApplicationResult noUndo = service.apply(modelId, diagnosticId, "action:no-undo", new QuickFixExecutionRequest(Map.of(), List.of()));
        assertThat(noUndo.status()).isEqualTo(QuickFixApplicationStatus.APPLIED_RESOLVED);
        assertThat(noUndo.undoAvailable()).isFalse();
        assertThat(onDisk()).contains("fixed");
        assertThatThrownBy(() -> service.undo(modelId)).isInstanceOf(QuickFixRuntimeException.class)
                .extracting(e -> ((QuickFixRuntimeException) e).getReason()).isEqualTo(QuickFixRuntimeException.Reason.UNDO_NOT_AVAILABLE);
    }

    // ---- helpers (oraculo independiente: se lee el XMI escrito en disco) ----

    private ModelValidationDiagnostic diagnosticOf(String ruleId, String elementId) {
        return orchestrator.latest(modelId).orElseThrow().diagnostics().stream()
                .filter(d -> d.ruleId().equals(ruleId) && d.targetElementId().equals(elementId)).findFirst().orElseThrow();
    }

    private byte[] savedBytes() throws Exception {
        ModelDocumentResponse saved = modelService.save(modelId);
        return Files.readAllBytes(storage.models().resolve(saved.fileName()));
    }

    private String onDisk() throws Exception {
        return new String(savedBytes(), StandardCharsets.UTF_8);
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

    private static ExpressionDefinition nameCompare(String id, BinaryExpressionOperator operator, String literal) {
        return new ExpressionDefinition(id, id, "", NODE, ExpressionValueType.scalar(ExpressionValueKind.BOOLEAN),
                new BinaryExpressionNode("node:" + id + ":cmp", operator,
                        new AttributeAccessExpressionNode("node:" + id + ":name", new SelfExpressionNode("node:" + id + ":self"), NAME),
                        new LiteralExpressionNode("node:" + id + ":lit", ExpressionValueType.scalar(ExpressionValueKind.STRING), literal)), Map.of());
    }

    private static ExpressionModel expressionModel() {
        return new ExpressionModel("1.0", METAMODEL, METAMODEL, List.of(
                nameCompare("expression:name-not-invalid", BinaryExpressionOperator.NOT_EQUALS, "INVALID"),
                nameCompare("expression:name-not-bad", BinaryExpressionOperator.NOT_EQUALS, "BAD"),
                nameCompare("expression:name-is-fixed", BinaryExpressionOperator.EQUALS, "fixed")), Map.of());
    }

    private static SetAttributeOperation set(String id, String attribute, ActionValueSource value) {
        return new SetAttributeOperation(id, new ActionSelector(ActionSelectorKind.SELF, "", ""), attribute, value);
    }

    private static ActionModel actionModel() {
        ActionDefinition reset = new ActionDefinition("action:reset", "Reset name", "", true, List.of(), "",
                List.of(set("operation:reset", NAME, ActionValueSource.literal("fixed"))), ActionTransactionPolicy.ATOMIC, ActionUndoPolicy.REQUIRED, Map.of());
        ActionDefinition renameTo = new ActionDefinition("action:rename-to", "Rename to", "",
                true, List.of(new ActionParameter("newName", ActionParameterType.STRING, true)), "",
                List.of(set("operation:rename", NAME, ActionValueSource.parameter("newName"))), ActionTransactionPolicy.ATOMIC, ActionUndoPolicy.REQUIRED, Map.of());
        ActionDefinition noop = new ActionDefinition("action:noop", "Keep name", "", true, List.of(), "",
                List.of(set("operation:noop", NAME, ActionValueSource.literal("INVALID"))), ActionTransactionPolicy.ATOMIC, ActionUndoPolicy.REQUIRED, Map.of());
        ActionDefinition touch = new ActionDefinition("action:touch-priority", "Touch priority", "", true, List.of(), "",
                List.of(set("operation:touch", PRIORITY, ActionValueSource.literal(5))), ActionTransactionPolicy.ATOMIC, ActionUndoPolicy.REQUIRED, Map.of());
        ActionDefinition noUndo = new ActionDefinition("action:no-undo", "Reset without undo", "", true, List.of(), "",
                List.of(set("operation:no-undo", NAME, ActionValueSource.literal("fixed"))), ActionTransactionPolicy.BEST_EFFORT, ActionUndoPolicy.NOT_SUPPORTED, Map.of());
        ActionDefinition guarded = new ActionDefinition("action:guarded", "Guarded reset", "", true, List.of(), "expression:name-is-fixed",
                List.of(set("operation:guarded", NAME, ActionValueSource.literal("fixed"))), ActionTransactionPolicy.ATOMIC, ActionUndoPolicy.REQUIRED, Map.of());
        return new ActionModel("1.0", METAMODEL, METAMODEL, METAMODEL, List.of(reset, renameTo, noop, touch, noUndo, guarded), Map.of());
    }

    private static ValidationRule rule(String id, String code, String expressionId, List<String> fixes) {
        return new ValidationRule(id, code, code, "", true, new ValidationContext(ValidationContextKind.ATTRIBUTE, NODE, NAME),
                ValidationSeverity.ERROR, expressionId, "${elementId} has a bad name", List.of(ValidationTrigger.FULL_VALIDATION, ValidationTrigger.ON_ATTRIBUTE_CHANGE),
                List.of(new ValidationDependency(ValidationDependencyKind.ATTRIBUTE, NAME)), fixes, ValidationExecutionMode.INCREMENTAL,
                AuthoritativeRepresentation.CANONICAL, ConversionStatus.FULL, null, Map.of());
    }

    private static ValidationModel validationModel() {
        ValidationRule a = rule(RULE_A, "NAME_NOT_INVALID", "expression:name-not-invalid",
                List.of("action:reset", "action:rename-to", "action:noop", "action:touch-priority", "action:no-undo", "action:guarded"));
        ValidationRule b = rule(RULE_B, "NAME_NOT_BAD", "expression:name-not-bad", List.of());
        return new ValidationModel("1.0", METAMODEL, METAMODEL, METAMODEL, METAMODEL,
                List.of(new ValidationRuleSet("set:default", "Default", "", true, 0, List.of(a.id(), b.id()), Map.of())), List.of(a, b), List.of(), Map.of());
    }
}
