package com.example.editor.validation.quickfix;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.NodeList;

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
import com.example.editor.metamodel.core.MetaOperation;
import com.example.editor.metamodel.core.MetaOperationBehaviorKind;
import com.example.editor.metamodel.core.MetaParameter;
import com.example.editor.metamodel.core.MetaPrimitiveType;
import com.example.editor.metamodel.core.MetaRelation;
import com.example.editor.metamodel.core.MetaRelationKind;
import com.example.editor.metamodel.core.MetaTypeReference;
import com.example.editor.metamodel.core.Multiplicity;
import com.example.editor.metamodel.core.OperationBehaviorModel;
import com.example.editor.metamodel.core.OperationBinding;
import com.example.editor.metamodel.core.OperationImplementationKind;
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
import com.example.editor.validation.action.ActionOperation;
import com.example.editor.validation.action.ActionParameter;
import com.example.editor.validation.action.ActionParameterType;
import com.example.editor.validation.action.ActionSelector;
import com.example.editor.validation.action.ActionSelectorKind;
import com.example.editor.validation.action.ActionTransactionPolicy;
import com.example.editor.validation.action.ActionUndoPolicy;
import com.example.editor.validation.action.ActionValueSource;
import com.example.editor.validation.action.CanonicalActionExecutor;
import com.example.editor.validation.action.CreateElementOperation;
import com.example.editor.validation.action.ExecuteActionOperation;
import com.example.editor.validation.action.InvokeOperationOperation;
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
 * Evidencia real y sin mocks de validation_author_quickfix.sl (selectores propio, seleccion, parametro,
 * elemento creado e identificador fijo), .vs (origen del valor: literal, expresion y parametro pedido) y
 * .ag (argumentos de una accion anidada y de una operacion invocada). Misma pila real que
 * QuickFixApplyUndoRedoRuntimeIntegrationTests (metamodelo Ecore real, orquestador, servicio de quick fix,
 * disco temporal). El oraculo es el XMI escrito en disco, leido como XML por el propio test.
 */
class QuickFixSelectorValueArgumentRuntimeIntegrationTests {

    private static final String METAMODEL = "introspection";
    private static final String NS_URI = "https://example.com/introspection";
    private static final String MODEL_CLASS_ID = NS_URI + "#//Model";
    private static final String NODE_CLASS_ID = NS_URI + "#//Node";
    private static final String MODEL = "class:Model";
    private static final String NODE = "class:Node";
    private static final String NAME = "attribute:Node:name";
    private static final String PRIORITY = "attribute:Node:priority";
    private static final String ELEMENTS = "relation:Model:elements";
    private static final String RENAME_OPERATION = "operation:Node:rename";
    private static final String RULE_A = "rule:name-not-invalid";

    private static final List<String> ACTIONS = List.of("action:to-self", "action:to-selection", "action:to-parameter",
            "action:to-optional-parameter", "action:to-element-id", "action:to-missing-element", "action:to-created",
            "action:value-expression", "action:value-requested", "action:rename-to", "action:outer", "action:invoke-rename");

    @TempDir
    Path temporaryDirectory;

    private StorageProperties storage;
    private ModelDocumentService modelService;
    private ValidationOrchestrator orchestrator;
    private QuickFixRuntimeService service;
    private OperationBehaviorModelApplicationService behaviorService;
    private String modelId;
    private String rootId;
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

        ModelDocumentResponse doc = modelService.create(METAMODEL, MODEL_CLASS_ID);
        modelId = doc.id();
        rootId = doc.rootElementId();
        doc = modelService.addElement(modelId, rootId, "elements", NODE_CLASS_ID);
        node1 = doc.elements().get(1).id();
        doc = modelService.addElement(modelId, rootId, "elements", NODE_CLASS_ID);
        node2 = doc.elements().get(2).id();
        modelService.setAttribute(modelId, node1, "name", "INVALID");
        modelService.setAttribute(modelId, node2, "name", "BAD");

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
        behaviorService = new OperationBehaviorModelApplicationService(behaviorRepository,
                metamodelService, expressionRepository, actionRepository, new OperationBehaviorModelValidator());
        ValidationRuntimeApplicationService runtime = new ValidationRuntimeApplicationService(metamodelService, expressions, actions,
                validations, behaviorService, behaviorRepository, new ValidationCompiler(new ValidationModelChecker(typeChecker),
                        new ValidationPlanCache()), new ValidationEngine(evaluator, new OperationQueryRuntime(evaluator)), null,
                new ValidationRuntimeProperties());

        InMemoryCanonicalEventBus bus = new InMemoryCanonicalEventBus();
        ValidationOrchestrationProperties orchestrationProperties = new ValidationOrchestrationProperties();
        orchestrator = new ValidationOrchestrator(runtime, metamodelService, modelService,
                new DefaultValidationBlockingPolicy(orchestrationProperties), orchestrationProperties, bus);
        service = new QuickFixRuntimeService(metamodelService, behaviorService, expressionRepository, actionRepository,
                new CanonicalActionExecutor(evaluator, new OperationQueryRuntime(evaluator)), modelService, orchestrator,
                new CanonicalModelChangeAnalyzer(), bus);
        orchestrator.validateLive(modelId, LiveValidationRequest.full());
    }

    // ---- sl: selectores ----

    @Test
    @DisplayName("validation_author_quickfix.sl: el selector propio actua sobre el elemento del diagnostico y nada mas")
    void selfSelectorTouchesOnlyTheDiagnosticElement() throws Exception {
        QuickFixApplicationResult result = apply("action:to-self", Map.of(), List.of());

        assertThat(result.status()).isEqualTo(QuickFixApplicationStatus.APPLIED_RESOLVED);
        assertThat(nodeNamed("fixed")).containsEntry("priority", "6");
        assertThat(nodeNamed("BAD")).doesNotContainKey("priority");
    }

    @Test
    @DisplayName("validation_author_quickfix.sl: el selector de seleccion usa el primer elemento seleccionado")
    void selectionSelectorUsesTheFirstSelectedElement() throws Exception {
        apply("action:to-selection", Map.of(), List.of(node2, node1));

        assertThat(nodeNamed("BAD")).containsEntry("priority", "7");
        assertThat(nodeNamed("fixed")).doesNotContainKey("priority");
    }

    @Test
    @DisplayName("validation_author_quickfix.sl: el selector de parametro usa el identificador recibido")
    void parameterSelectorUsesTheSuppliedElementId() throws Exception {
        apply("action:to-parameter", Map.of("target", node2), List.of());

        assertThat(nodeNamed("BAD")).containsEntry("priority", "8");
        assertThat(nodeNamed("fixed")).doesNotContainKey("priority");
    }

    @Test
    @DisplayName("validation_author_quickfix.sl: el selector de identificador fijo apunta a ese elemento")
    void elementIdSelectorPointsAtTheFixedElement() throws Exception {
        apply("action:to-element-id", Map.of(), List.of());

        assertThat(nodeNamed("BAD")).containsEntry("priority", "9");
        assertThat(nodeNamed("fixed")).doesNotContainKey("priority");
    }

    @Test
    @DisplayName("validation_author_quickfix.sl: el selector de elemento creado alcanza al elemento nuevo")
    void createdElementSelectorReachesTheNewElement() throws Exception {
        assertThat(nodesOnDisk()).hasSize(2);

        apply("action:to-created", Map.of(), List.of());

        List<Map<String, String>> nodes = nodesOnDisk();
        assertThat(nodes).hasSize(3);
        assertThat(nodes).extracting(node -> node.get("name")).containsExactlyInAnyOrder("fixed", "BAD", "created-node");
        assertThat(nodeNamed("created-node")).containsEntry("priority", "3");
    }

    @Test
    @DisplayName("validation_author_quickfix.sl: selectores sin destino se rechazan y el modelo queda identico")
    void selectorsWithoutATargetAreRejectedAndTheModelIsUntouched() throws Exception {
        byte[] before = savedBytes();

        assertThatThrownBy(() -> apply("action:to-optional-parameter", Map.of(), List.of()))
                .isInstanceOf(QuickFixRuntimeException.class).hasMessageContaining("Parameter does not contain an element id: target");
        assertThatThrownBy(() -> apply("action:to-missing-element", Map.of(), List.of()))
                .isInstanceOf(QuickFixRuntimeException.class).hasMessageContaining("Selected element was not found: element-999");
        assertThatThrownBy(() -> apply("action:to-selection", Map.of(), List.of()))
                .isInstanceOf(QuickFixRuntimeException.class).hasMessageContaining("Selected element was not found");
        assertThatThrownBy(() -> apply("action:to-parameter", Map.of(), List.of()))
                .isInstanceOf(QuickFixRuntimeException.class).hasMessageContaining("Required action parameter is missing: target");

        assertThat(savedBytes()).isEqualTo(before);
        assertThat(nodeNamed("INVALID")).doesNotContainKey("priority");
    }

    // ---- vs: origen del valor ----

    @Test
    @DisplayName("validation_author_quickfix.vs: un valor calculado por expresion se escribe en el modelo")
    void expressionValueSourceIsEvaluatedAgainstTheModel() throws Exception {
        QuickFixApplicationResult result = apply("action:value-expression", Map.of(), List.of());

        assertThat(result.diagnosticResolved()).isTrue();
        assertThat(nodesOnDisk()).extracting(node -> node.get("name")).containsExactlyInAnyOrder("INVALID-by-expression", "BAD");
    }

    @Test
    @DisplayName("validation_author_quickfix.vs: un valor pedido al usuario se escribe tal cual y su ausencia se rechaza")
    void requestedParameterValueIsWrittenAndItsAbsenceIsRejected() throws Exception {
        byte[] before = savedBytes();
        assertThatThrownBy(() -> apply("action:value-requested", Map.of(), List.of()))
                .isInstanceOf(QuickFixRuntimeException.class).hasMessageContaining("Required action parameter is missing: requestedName");
        assertThat(savedBytes()).isEqualTo(before);

        apply("action:value-requested", Map.of("requestedName", "Typed by the user"), List.of());

        assertThat(nodesOnDisk()).extracting(node -> node.get("name")).containsExactlyInAnyOrder("Typed by the user", "BAD");
    }

    // ---- ag: argumentos ----

    @Test
    @DisplayName("validation_author_quickfix.ag: los argumentos de una accion anidada llegan a su parametro")
    void nestedActionArgumentsReachTheNestedParameter() throws Exception {
        apply("action:outer", Map.of(), List.of());

        assertThat(nodesOnDisk()).extracting(node -> node.get("name")).containsExactlyInAnyOrder("nested-name", "BAD");
    }

    @Test
    @DisplayName("validation_author_quickfix.ag: los argumentos de una operacion invocada llegan a la accion enlazada")
    void invokedOperationArgumentsReachTheBoundAction() throws Exception {
        behaviorService.create(METAMODEL, new OperationBehaviorModel("1.0", METAMODEL,
                List.of(new OperationBinding(RENAME_OPERATION, OperationImplementationKind.ACTION, "", "action:rename-to", "", Map.of())), Map.of()));

        apply("action:invoke-rename", Map.of(), List.of());

        assertThat(nodesOnDisk()).extracting(node -> node.get("name")).containsExactlyInAnyOrder("invoked-name", "BAD");
    }

    // ---- ayudas (oraculo independiente: el XMI escrito en disco, leido como XML) ----

    private QuickFixApplicationResult apply(String actionId, Map<String, Object> arguments, List<String> selection) {
        orchestrator.validateLive(modelId, LiveValidationRequest.full());
        ModelValidationDiagnostic diagnostic = orchestrator.latest(modelId).orElseThrow().diagnostics().stream()
                .filter(item -> item.ruleId().equals(RULE_A) && item.targetElementId().equals(node1)).findFirst().orElseThrow();
        return service.apply(modelId, diagnostic.id(), actionId, new QuickFixExecutionRequest(arguments, selection));
    }

    private byte[] savedBytes() throws Exception {
        ModelDocumentResponse saved = modelService.save(modelId);
        return Files.readAllBytes(storage.models().resolve(saved.fileName()));
    }

    /** Atributos XML de cada elemento contenido bajo la raiz del modelo guardado. */
    private List<Map<String, String>> nodesOnDisk() throws Exception {
        org.w3c.dom.Document xml = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new ByteArrayInputStream(savedBytes()));
        NodeList children = xml.getDocumentElement().getElementsByTagName("elements");
        List<Map<String, String>> nodes = new ArrayList<>();
        for (int index = 0; index < children.getLength(); index++) {
            NamedNodeMap attributes = ((Element) children.item(index)).getAttributes();
            Map<String, String> values = new LinkedHashMap<>();
            for (int attribute = 0; attribute < attributes.getLength(); attribute++) {
                values.put(attributes.item(attribute).getNodeName(), attributes.item(attribute).getNodeValue());
            }
            nodes.add(values);
        }
        return nodes;
    }

    private Map<String, String> nodeNamed(String name) throws Exception {
        var nodes = nodesOnDisk();
        return nodes.stream().filter(node -> name.equals(node.get("name"))).findFirst()
                .orElseThrow(() -> new AssertionError("No node named " + name + " in " + nodes));
    }

    private static MetaModel canonicalMetamodel() {
        MetaAttribute name = new MetaAttribute(NAME, "name", MetaTypeReference.primitive(MetaPrimitiveType.STRING), Multiplicity.optional(), true, true, "", Map.of());
        MetaAttribute priority = new MetaAttribute(PRIORITY, "priority", MetaTypeReference.primitive(MetaPrimitiveType.INTEGER), Multiplicity.optional(), true, true, "", Map.of());
        MetaParameter newName = new MetaParameter("parameter:Node:rename:newName", "newName", MetaTypeReference.primitive(MetaPrimitiveType.STRING),
                Multiplicity.one(), true, true, Map.of());
        MetaOperation rename = new MetaOperation(RENAME_OPERATION, "rename", null, null, true, true, List.of(newName), List.of(),
                MetaOperationBehaviorKind.COMMAND, Map.of());
        MetaClass node = new MetaClass(NODE, "Node", MetaClassKind.CLASS, List.of(name, priority), List.of(rename), Map.of());
        MetaClass model = new MetaClass(MODEL, "Model", MetaClassKind.CLASS, List.of(), List.of(), Map.of());
        MetaRelation elements = new MetaRelation(ELEMENTS, "elements", MODEL, NODE, MetaRelationKind.CONTAINMENT,
                Multiplicity.one(), Multiplicity.many(), "", true, true, Map.of());
        return new MetaModel(MetaModel.CURRENT_SCHEMA_VERSION, METAMODEL, "Introspection", List.of(model, node), List.of(), List.of(), List.of(elements), Map.of());
    }

    private static ExpressionDefinition nameCompare(String id, BinaryExpressionOperator operator, String literal) {
        return new ExpressionDefinition(id, id, "", NODE, ExpressionValueType.scalar(ExpressionValueKind.BOOLEAN),
                new BinaryExpressionNode("node:" + id + ":cmp", operator,
                        new AttributeAccessExpressionNode("node:" + id + ":name", new SelfExpressionNode("node:" + id + ":self"), NAME),
                        new LiteralExpressionNode("node:" + id + ":lit", ExpressionValueType.scalar(ExpressionValueKind.STRING), literal)), Map.of());
    }

    private static ExpressionDefinition suffixedName() {
        String id = "expression:name-suffixed";
        return new ExpressionDefinition(id, id, "", NODE, ExpressionValueType.scalar(ExpressionValueKind.STRING),
                new BinaryExpressionNode("node:" + id + ":add", BinaryExpressionOperator.ADD,
                        new AttributeAccessExpressionNode("node:" + id + ":name", new SelfExpressionNode("node:" + id + ":self"), NAME),
                        new LiteralExpressionNode("node:" + id + ":lit", ExpressionValueType.scalar(ExpressionValueKind.STRING), "-by-expression")), Map.of());
    }

    private static ExpressionModel expressionModel() {
        return new ExpressionModel("1.0", METAMODEL, METAMODEL, List.of(
                nameCompare("expression:name-not-invalid", BinaryExpressionOperator.NOT_EQUALS, "INVALID"), suffixedName()), Map.of());
    }

    private static ActionSelector self() { return new ActionSelector(ActionSelectorKind.SELF, "", ""); }

    private static SetAttributeOperation set(String id, ActionSelector target, String attribute, ActionValueSource value) {
        return new SetAttributeOperation(id, target, attribute, value);
    }

    private static ActionDefinition action(String id, List<ActionParameter> parameters, ActionOperation... operations) {
        return new ActionDefinition(id, id, "", true, parameters, "", List.of(operations), ActionTransactionPolicy.ATOMIC,
                ActionUndoPolicy.REQUIRED, Map.of());
    }

    private ActionModel actionModel() {
        ActionOperation fixSelf = set("operation:fix-self", self(), NAME, ActionValueSource.literal("fixed"));
        ActionParameter target = new ActionParameter("target", ActionParameterType.ELEMENT_ID, true);
        ActionParameter optionalTarget = new ActionParameter("target", ActionParameterType.ELEMENT_ID, false);
        ActionParameter newName = new ActionParameter("newName", ActionParameterType.STRING, true);
        List<ActionDefinition> definitions = List.of(
                action("action:to-self", List.of(), fixSelf, set("operation:self-priority", self(), PRIORITY, ActionValueSource.literal(6))),
                action("action:to-selection", List.of(), fixSelf, set("operation:selection-priority",
                        new ActionSelector(ActionSelectorKind.SELECTION, "", ""), PRIORITY, ActionValueSource.literal(7))),
                action("action:to-parameter", List.of(target), fixSelf, set("operation:parameter-priority",
                        new ActionSelector(ActionSelectorKind.PARAMETER, "target", ""), PRIORITY, ActionValueSource.literal(8))),
                action("action:to-optional-parameter", List.of(optionalTarget), fixSelf, set("operation:optional-priority",
                        new ActionSelector(ActionSelectorKind.PARAMETER, "target", ""), PRIORITY, ActionValueSource.literal(8))),
                action("action:to-element-id", List.of(), fixSelf, set("operation:element-priority",
                        new ActionSelector(ActionSelectorKind.ELEMENT_ID, "", node2), PRIORITY, ActionValueSource.literal(9))),
                action("action:to-missing-element", List.of(), fixSelf, set("operation:missing-priority",
                        new ActionSelector(ActionSelectorKind.ELEMENT_ID, "", "element-999"), PRIORITY, ActionValueSource.literal(9))),
                action("action:to-created", List.of(), fixSelf,
                        new CreateElementOperation("operation:create-node", NODE, "fresh", ELEMENTS,
                                new ActionSelector(ActionSelectorKind.ELEMENT_ID, "", rootId)),
                        set("operation:created-name", new ActionSelector(ActionSelectorKind.CREATED_ELEMENT, "fresh", ""), NAME, ActionValueSource.literal("created-node")),
                        set("operation:created-priority", new ActionSelector(ActionSelectorKind.CREATED_ELEMENT, "fresh", ""), PRIORITY, ActionValueSource.literal(3))),
                action("action:value-expression", List.of(), set("operation:expression-name", self(), NAME,
                        ActionValueSource.expression("expression:name-suffixed"))),
                action("action:value-requested", List.of(new ActionParameter("requestedName", ActionParameterType.STRING, true)),
                        set("operation:requested-name", self(), NAME, ActionValueSource.parameter("requestedName"))),
                action("action:rename-to", List.of(newName), set("operation:rename", self(), NAME, ActionValueSource.parameter("newName"))),
                action("action:outer", List.of(), new ExecuteActionOperation("operation:run-nested", "action:rename-to",
                        Map.of("newName", ActionValueSource.literal("nested-name")))),
                action("action:invoke-rename", List.of(), new InvokeOperationOperation("operation:invoke", self(), RENAME_OPERATION,
                        List.of(ActionValueSource.literal("invoked-name")))));
        return new ActionModel("1.0", METAMODEL, METAMODEL, METAMODEL, definitions, Map.of());
    }

    private static ValidationModel validationModel() {
        ValidationRule rule = new ValidationRule(RULE_A, "NAME_NOT_INVALID", "NAME_NOT_INVALID", "", true,
                new ValidationContext(ValidationContextKind.ATTRIBUTE, NODE, NAME), ValidationSeverity.ERROR, "expression:name-not-invalid",
                "${elementId} has a bad name", List.of(ValidationTrigger.FULL_VALIDATION, ValidationTrigger.ON_ATTRIBUTE_CHANGE),
                List.of(new ValidationDependency(ValidationDependencyKind.ATTRIBUTE, NAME)), ACTIONS, ValidationExecutionMode.INCREMENTAL,
                AuthoritativeRepresentation.CANONICAL, ConversionStatus.FULL, null, Map.of());
        return new ValidationModel("1.0", METAMODEL, METAMODEL, METAMODEL, METAMODEL,
                List.of(new ValidationRuleSet("set:default", "Default", "", true, 0, List.of(rule.id()), Map.of())), List.of(rule), List.of(), Map.of());
    }
}
