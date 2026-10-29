package com.example.editor.gamification.feedback;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
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
import com.example.editor.event.EventContext;
import com.example.editor.event.EventContextKind;
import com.example.editor.event.EventModelApplicationService;
import com.example.editor.event.EventModelChecker;
import com.example.editor.event.EventModelProperties;
import com.example.editor.event.EventSource;
import com.example.editor.event.bus.InMemoryCanonicalEventBus;
import com.example.editor.event.persistence.JsonEventBindingModelRepository;
import com.example.editor.event.persistence.JsonEventDefinitionModelRepository;
import com.example.editor.gamification.GamificationApplicationService;
import com.example.editor.gamification.GamificationBindingModel;
import com.example.editor.gamification.GamificationChecker;
import com.example.editor.gamification.GamificationCorruptDocumentRecovery;
import com.example.editor.gamification.GamificationDefinitionModel;
import com.example.editor.gamification.GamificationEffectKind;
import com.example.editor.gamification.GamificationProperties;
import com.example.editor.gamification.GamificationRepeatPolicy;
import com.example.editor.gamification.GamificationRule;
import com.example.editor.gamification.PointTypeDefinition;
import com.example.editor.gamification.persistence.JsonGamificationBindingRepository;
import com.example.editor.gamification.persistence.JsonGamificationDefinitionRepository;
import com.example.editor.gamification.persistence.JsonGamificationRuntimeRepository;
import com.example.editor.gamification.runtime.GamificationRuntimeService;
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
import com.example.editor.validation.quickfix.CanonicalModelChangeAnalyzer;
import com.example.editor.validation.quickfix.QuickFixApplicationResult;
import com.example.editor.validation.quickfix.QuickFixApplicationStatus;
import com.example.editor.validation.quickfix.QuickFixExecutionRequest;
import com.example.editor.validation.quickfix.QuickFixRuntimeService;
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
 * Evidencia real, sin mocks, de la cadena validar, corregir, revalidar y recompensar
 * (x_validate_fix_revalidate_reward.fb y .per): una correccion rapida real sobre una sesion EMF viva publica
 * en el bus real, el runtime de gamificacion suscrito concede puntos, el libro y el estado JSON quedan en
 * disco y el aviso se proyecta desde el libro. Los oraculos son el XMI, el libro y el estado leidos del
 * disco y los puntos calculados a mano.
 *
 * <p>Hallazgo: el evento de problema resuelto no lleva el identificador del diagnostico, asi que deshacer la
 * correccion NO compensa los puntos en la cadena real. Un caso lo documenta con aserciones y otro demuestra
 * que la compensacion y su aviso funcionan cuando el evento referencia el evento premiado.
 */
class ValidateFixRewardChainIntegrationTests {

    private static final String METAMODEL = "introspection";
    private static final String NS_URI = "https://example.com/introspection";
    private static final String MODEL_CLASS_ID = NS_URI + "#//Model";
    private static final String NODE_CLASS_ID = NS_URI + "#//Node";
    private static final String NODE = "class:Node";
    private static final String NAME = "attribute:Node:name";
    private static final String PRIORITY = "attribute:Node:priority";
    private static final String RULE = "rule:name-not-invalid";
    private static final String FIX = "action:reset";
    private static final String RESOLVED = "core.validation.violation-resolved";
    private static final String POINTS = "points:quality";

    @TempDir
    Path temporaryDirectory;

    private StorageProperties storage;
    private JsonMapper mapper;
    private ModelDocumentService modelService;
    private ValidationOrchestrator orchestrator;
    private QuickFixRuntimeService quickFixes;
    private GamificationRuntimeService gamification;
    private InMemoryCanonicalEventBus bus;
    private Path gamificationDirectory;
    private final List<CanonicalEvent> published = new ArrayList<>();
    private String modelId;
    private String node1;

    @BeforeEach
    void setUp() throws Exception {
        storage = new StorageProperties(temporaryDirectory.resolve("metamodels"), temporaryDirectory.resolve("models"),
                temporaryDirectory.resolve("notations"), temporaryDirectory.resolve("validations"), temporaryDirectory.resolve("quickfixes"),
                temporaryDirectory.resolve("generators"), temporaryDirectory.resolve("assets"), temporaryDirectory.resolve("exports"), true);
        Files.createDirectories(storage.metamodels());
        mapper = JsonMapper.builder().findAndAddModules().build();

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

        bus = new InMemoryCanonicalEventBus();
        bus.subscribe(event -> true, published::add);
        ValidationOrchestrationProperties orchestrationProperties = new ValidationOrchestrationProperties();
        orchestrator = new ValidationOrchestrator(runtime, metamodelService, modelService,
                new DefaultValidationBlockingPolicy(orchestrationProperties), orchestrationProperties, bus);
        quickFixes = new QuickFixRuntimeService(metamodelService, behaviorService, expressionRepository, actionRepository,
                new CanonicalActionExecutor(evaluator, new OperationQueryRuntime(evaluator)), modelService, orchestrator,
                new CanonicalModelChangeAnalyzer(), bus);

        EventModelProperties eventProperties = new EventModelProperties();
        eventProperties.setDirectory(temporaryDirectory.resolve("events"));
        EventModelApplicationService events = new EventModelApplicationService(
                new JsonEventDefinitionModelRepository(eventProperties, mapper), new JsonEventBindingModelRepository(eventProperties, mapper),
                expressionRepository, metamodelService, new EventModelChecker());
        GamificationProperties properties = new GamificationProperties();
        gamificationDirectory = temporaryDirectory.resolve("gamification");
        properties.setDirectory(gamificationDirectory);
        GamificationApplicationService application = new GamificationApplicationService(
                new JsonGamificationDefinitionRepository(properties, mapper), new JsonGamificationBindingRepository(properties, mapper),
                metamodelService, events, new GamificationChecker(), new GamificationCorruptDocumentRecovery(properties));
        application.create(METAMODEL,
                new GamificationDefinitionModel("1.0", "gamification-definitions", METAMODEL, "Gamification",
                        List.of(new PointTypeDefinition(POINTS, "Quality", "", 0L)), List.of(), List.of(), List.of(), List.of(), Map.of()),
                new GamificationBindingModel("1.0", "gamification-bindings", METAMODEL, "gamification-definitions",
                        List.of(new GamificationRule("rule:award-fix", "rule:award-fix", RESOLVED, "", "EQUALS", null, POINTS, 5, "", "", "",
                                GamificationRepeatPolicy.ONCE_PER_EVENT, 0, 100, 100, GamificationEffectKind.AWARD, true)), Map.of()));
        gamification = new GamificationRuntimeService(application, new JsonGamificationRuntimeRepository(properties, mapper), properties, bus);
        gamification.subscribe();

        ModelDocumentResponse doc = modelService.create(METAMODEL, MODEL_CLASS_ID);
        modelId = doc.id();
        doc = modelService.addElement(modelId, doc.rootElementId(), "elements", NODE_CLASS_ID);
        node1 = doc.elements().get(1).id();
        modelService.setAttribute(modelId, node1, "name", "INVALID");
        orchestrator.validateLive(modelId, LiveValidationRequest.full());
    }

    @Test
    @DisplayName("x_validate_fix_revalidate_reward.fb/.per: corregir concede 5 puntos, aviso de recompensa y todo queda en disco")
    void fixingTheModelAwardsPointsShowsTheNoticeAndPersistsEverything() throws Exception {
        String diagnosticId = diagnostic().id();

        QuickFixApplicationResult result = quickFixes.apply(modelId, diagnosticId, FIX, new QuickFixExecutionRequest(Map.of(), List.of()));

        assertThat(result.status()).isEqualTo(QuickFixApplicationStatus.APPLIED_RESOLVED);
        // Modelo corregido en disco (XMI leido directamente).
        String xmi = modelOnDisk();
        assertThat(xmi).contains("fixed").doesNotContain("INVALID");
        // Libro en disco: una unica entrada activa de 5 puntos ligada al diagnostico resuelto.
        List<Map<?, ?>> entries = ledgerEntries();
        assertThat(entries).hasSize(1);
        Map<?, ?> entry = entries.get(0);
        assertThat(entry.get("ruleId")).isEqualTo("rule:award-fix");
        assertThat(entry.get("status")).isEqualTo("ACTIVE");
        assertThat(entry.get("pointsTypeId")).isEqualTo(POINTS);
        assertThat(((Number) entry.get("pointsDelta")).longValue()).isEqualTo(5L);
        assertThat(((Map<?, ?>) entry.get("metadata")).get("eventTypeId")).isEqualTo(RESOLVED);
        assertThat(((Map<?, ?>) entry.get("metadata")).get("subjectId")).isEqualTo(diagnosticId);
        // Estado en disco: 5 puntos calculados a mano para el actor del sistema.
        Map<?, ?> actors = (Map<?, ?>) readJson("introspection.gamification-state.json").get("actors");
        assertThat(actors).hasSize(1);
        assertThat(actors.get("system")).isNotNull();
        assertThat(((Number) ((Map<?, ?>) ((Map<?, ?>) actors.get("system")).get("points")).get(POINTS)).longValue()).isEqualTo(5L);
        // Aviso proyectado desde el libro.
        List<UserFeedbackEnvelope> notices = gamification.feedback(METAMODEL, "system", 10);
        assertThat(notices).hasSize(1);
        assertThat(notices.get(0).kind()).isEqualTo(UserFeedbackKind.POINTS_AWARDED);
        assertThat(notices.get(0).pointsDelta()).isEqualTo(5L);
        assertThat(notices.get(0).title()).isEqualTo("+5 Quality");
    }

    @Test
    @DisplayName("x_validate_fix_revalidate_reward.fb: deshacer la correccion compensa los puntos porque el evento premiado lleva el diagnostico")
    void undoingTheFixCompensatesThePointsBecauseTheResolvedEventCarriesTheDiagnosticId() throws Exception {
        String diagnosticId = diagnostic().id();
        quickFixes.apply(modelId, diagnosticId, FIX, new QuickFixExecutionRequest(Map.of(), List.of()));

        QuickFixApplicationResult undone = quickFixes.undo(modelId);

        assertThat(undone.status()).isEqualTo(QuickFixApplicationStatus.UNDONE);
        assertThat(modelOnDisk()).contains("INVALID").doesNotContain("fixed");
        CanonicalEvent resolved = published.stream().filter(event -> event.eventTypeId().equals(RESOLVED)).findFirst().orElseThrow();
        CanonicalEvent undoneEvent = published.stream().filter(event -> event.eventTypeId().equals("core.quickfix.undone")).findFirst().orElseThrow();
        // Tanto el evento que premia como el de deshacer llevan el diagnostico.
        assertThat(resolved.payload()).containsEntry("diagnosticId", diagnosticId);
        assertThat(undoneEvent.payload()).containsEntry("diagnosticId", diagnosticId);
        // Libro: la concesion queda compensada y aparece la reversion de -5 ligada a ella.
        List<Map<?, ?>> entries = ledgerEntries();
        assertThat(entries).hasSize(2);
        Map<?, ?> award = entries.stream().filter(entry -> "COMPENSATED".equals(entry.get("status"))).findFirst().orElseThrow();
        Map<?, ?> reversal = entries.stream().filter(entry -> !entry.get("id").equals(award.get("id"))).findFirst().orElseThrow();
        assertThat(((Number) award.get("pointsDelta")).longValue()).isEqualTo(5L);
        assertThat(((Number) reversal.get("pointsDelta")).longValue()).isEqualTo(-5L);
        assertThat(reversal.get("compensationOf")).isEqualTo(award.get("id"));
        assertThat((Map<?, ?>) readJson("introspection.gamification-state.json").get("actors")).isEmpty();
        assertThat(gamification.feedback(METAMODEL, "system", 10)).extracting(UserFeedbackEnvelope::kind)
                .containsExactly(UserFeedbackKind.AWARD_COMPENSATED);
    }

    @Test
    @DisplayName("x_validate_fix_revalidate_reward.fb: una referencia explicita al evento premiado revierte los puntos y muestra el aviso de reversion")
    void anExplicitReferenceToTheAwardedEventRevertsThePointsAndShowsTheReversalNotice() throws Exception {
        quickFixes.apply(modelId, diagnostic().id(), FIX, new QuickFixExecutionRequest(Map.of(), List.of()));
        Map<?, ?> awarded = ledgerEntries().get(0);
        String awardedEntryId = (String) awarded.get("id");
        String awardedEventId = (String) awarded.get("sourceEventId");

        bus.publish(new CanonicalEvent("1.0", "event:compensate-by-reference", "core.quickfix.undone", "1.0", Instant.parse("2026-07-28T10:00:00Z"),
                new EventSource("test", "gamification", "", "1.0"),
                new EventContext(EventContextKind.GAMIFICATION, "", "", METAMODEL, "model", "", "session:one"),
                null, null, "transaction:compensate", "correlation:compensate", "", Map.of("compensatesEventId", awardedEventId), Map.of()));

        List<Map<?, ?>> entries = ledgerEntries();
        assertThat(entries).hasSize(2);
        assertThat(entries).filteredOn(entry -> entry.get("id").equals(awardedEntryId)).singleElement()
                .satisfies(entry -> assertThat(entry.get("status")).isEqualTo("COMPENSATED"));
        Map<?, ?> reversal = entries.stream().filter(entry -> !entry.get("id").equals(awardedEntryId)).findFirst().orElseThrow();
        assertThat(((Number) reversal.get("pointsDelta")).longValue()).isEqualTo(-5L);
        assertThat(reversal.get("compensationOf")).isEqualTo(awardedEntryId);
        // Con la unica concesion revertida el actor desaparece del estado (5 - 5 = 0, calculado a mano).
        assertThat((Map<?, ?>) readJson("introspection.gamification-state.json").get("actors")).isEmpty();
        List<UserFeedbackEnvelope> notices = gamification.feedback(METAMODEL, "system", 10);
        assertThat(notices).extracting(UserFeedbackEnvelope::kind).containsExactly(UserFeedbackKind.AWARD_COMPENSATED);
        assertThat(notices.get(0).pointsDelta()).isEqualTo(-5L);
    }

    // ---- ayudas (oraculo independiente: ficheros leidos del disco) ----

    private ModelValidationDiagnostic diagnostic() {
        return orchestrator.latest(modelId).orElseThrow().diagnostics().stream()
                .filter(item -> item.ruleId().equals(RULE) && item.targetElementId().equals(node1)).findFirst().orElseThrow();
    }

    private String modelOnDisk() throws Exception {
        ModelDocumentResponse saved = modelService.save(modelId);
        return new String(Files.readAllBytes(storage.models().resolve(saved.fileName())), StandardCharsets.UTF_8);
    }

    private Map<?, ?> readJson(String fileName) throws Exception {
        return mapper.readValue(Files.readString(gamificationDirectory.resolve(fileName)), Map.class);
    }

    private List<Map<?, ?>> ledgerEntries() throws Exception {
        List<Map<?, ?>> result = new ArrayList<>();
        for (Object entry : (List<?>) readJson("introspection.gamification-ledger.json").get("entries")) result.add((Map<?, ?>) entry);
        return result;
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
