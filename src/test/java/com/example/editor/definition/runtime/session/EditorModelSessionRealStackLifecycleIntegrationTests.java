package com.example.editor.definition.runtime.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.editor.config.StorageProperties;
import com.example.editor.definition.EditorCapability;
import com.example.editor.definition.EditorRuntimePolicy;
import com.example.editor.definition.EditorValidationTrigger;
import com.example.editor.definition.runtime.session.persistence.JsonEditorModelSessionRepository;
import com.example.editor.event.bus.InMemoryCanonicalEventBus;
import com.example.editor.metamodel.application.CanonicalMetamodelApplicationService;
import com.example.editor.metamodel.behavior.OperationBehaviorModelApplicationService;
import com.example.editor.metamodel.behavior.OperationBehaviorModelProperties;
import com.example.editor.metamodel.behavior.OperationQueryRuntime;
import com.example.editor.metamodel.behavior.persistence.JsonOperationBehaviorModelRepository;
import com.example.editor.metamodel.validation.OperationBehaviorModelValidator;
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
import com.example.editor.validation.model.CanonicalModelElement;
import com.example.editor.validation.quickfix.CanonicalModelChangeAnalyzer;
import com.example.editor.validation.quickfix.QuickFixDescriptor;
import com.example.editor.validation.quickfix.QuickFixExecutionRequest;
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
 * Evidencia real, sin mocks de comportamiento, del ciclo de una sesion de editor: abrir (con
 * validacion al abrir segun la politica), crear y editar con etags encadenados, validar a mano,
 * aplicar un Quick Fix real con revalidacion incremental, guardar y reabrir con una instancia nueva
 * del repositorio JSON. Etapas: model_editor_session.op/ed/va/qf/sv.
 */
class EditorModelSessionRealStackLifecycleIntegrationTests {
    private static final String M = EditorSessionRealStackFixtures.METAMODEL;
    private static final String PERSON = EditorSessionRealStackFixtures.PERSON;
    private static final String NAME = EditorSessionRealStackFixtures.NAME;
    private static final String EDITOR = "editor-life";
    private static final String SESSION = "session-life";
    private static final String RULE = "rule:name-not-invalid";

    @TempDir Path temporary;
    private JsonMapper mapper;
    private EditorSessionProperties properties;
    private EditorModelSessionService service;

    @BeforeEach void setUp() {
        mapper = JsonMapper.builder().findAndAddModules().build();
        StorageProperties storage = EditorSessionRealStackFixtures.storage(temporary);
        CanonicalMetamodelApplicationService metamodels = EditorSessionRealStackFixtures.metamodels(temporary, mapper);
        ExpressionTypeChecker typeChecker = new ExpressionTypeChecker(new ExpressionDependencyAnalyzer());
        ExpressionEvaluator evaluator = new ExpressionEvaluator(typeChecker);
        JsonExpressionModelRepository expressionRepository = new JsonExpressionModelRepository(storage, new ExpressionModelProperties(), mapper);
        ExpressionModelApplicationService expressions = new ExpressionModelApplicationService(expressionRepository, metamodels, typeChecker, evaluator);
        expressions.create(M, expressionModel());
        JsonActionModelRepository actionRepository = new JsonActionModelRepository(storage, new ActionModelProperties(), mapper);
        ActionModelApplicationService actions = new ActionModelApplicationService(actionRepository, expressionRepository, metamodels, new ActionModelChecker(typeChecker));
        actions.create(M, actionModel());
        ValidationModelApplicationService validations = new ValidationModelApplicationService(
                new JsonValidationModelRepository(storage, new ValidationModelProperties(), mapper), expressionRepository, actionRepository,
                metamodels, new ValidationModelChecker(typeChecker));
        validations.create(M, validationModel());
        JsonOperationBehaviorModelRepository behaviorRepository = new JsonOperationBehaviorModelRepository(storage, new OperationBehaviorModelProperties(), mapper);
        OperationBehaviorModelApplicationService behaviors = new OperationBehaviorModelApplicationService(behaviorRepository, metamodels,
                expressionRepository, actionRepository, new OperationBehaviorModelValidator());
        ValidationRuntimeApplicationService validationRuntime = new ValidationRuntimeApplicationService(metamodels, expressions, actions,
                validations, behaviors, behaviorRepository, new ValidationCompiler(new ValidationModelChecker(typeChecker), new ValidationPlanCache()),
                new ValidationEngine(evaluator, new OperationQueryRuntime(evaluator)), null, new ValidationRuntimeProperties());
        CanonicalModelChangeAnalyzer changes = new CanonicalModelChangeAnalyzer();
        EditorSessionQuickFixRuntime quickFixes = new EditorSessionQuickFixRuntime(metamodels, behaviors, expressionRepository, actionRepository,
                new CanonicalActionExecutor(evaluator, new OperationQueryRuntime(evaluator)), changes);

        properties = new EditorSessionProperties();
        properties.setDirectory(temporary.resolve("sessions"));
        properties.setPendingEventSpoolDirectory(temporary.resolve("spool"));
        EditorRuntimePolicy policy = new EditorRuntimePolicy(Set.of(EditorValidationTrigger.ON_OPEN, EditorValidationTrigger.ON_CHANGE),
                false, true, false, false, false, 2, 30_000L);
        Set<EditorCapability> capabilities = Set.of(EditorCapability.MODEL_EDITING, EditorCapability.UNDO_REDO,
                EditorCapability.VALIDATION_RUNTIME, EditorCapability.QUICK_FIX_RUNTIME);
        service = new EditorModelSessionService(
                EditorSessionRealStackFixtures.runtimes(EditorSessionRealStackFixtures.editor(EDITOR, policy, capabilities)), metamodels,
                new JsonEditorModelSessionRepository(properties, mapper), null, properties, null, validationRuntime, changes, quickFixes,
                new EditorSessionEventPublisher(new InMemoryCanonicalEventBus()), expressions,
                new EditorSessionPendingEventBuffer(properties, mapper), null, null, null);
    }

    @Test void openEditValidateQuickFixSaveAndReopenWithAFreshRepository() throws IOException {
        // op: la sesion se abre con un elemento raiz y, segun la politica, se valida al abrir.
        EditorSessionView opened = service.create(M, EDITOR, new EditorSessionCreateRequest(SESSION, "model-life", "People",
                "representation:main", PERSON, Map.of(NAME, "INVALID")));
        assertThat(opened.session().model().elements()).hasSize(1);
        assertThat(opened.validationReport()).isNotNull();
        assertThat(opened.validationReport().diagnostics()).hasSize(1);
        assertThat(opened.validationReport().diagnostics().get(0).ruleId()).isEqualTo(RULE);

        // ed: crear y editar con etags encadenados; cada paso devuelve un etag nuevo y una revision mayor.
        EditorSessionView created = service.createNode(M, EDITOR, SESSION, "tool:create-person",
                new EditorNodeCreationRequest("representation:main", "", "", Map.of(NAME, "Bob")), opened.etag());
        assertThat(created.etag()).isNotEqualTo(opened.etag());
        assertThat(created.session().revision()).isGreaterThan(opened.session().revision());
        assertThat(created.session().model().elements()).hasSize(2);
        String bobId = created.session().model().elements().stream().filter(element -> "Bob".equals(element.attributes().get(NAME)))
                .findFirst().orElseThrow().id();
        EditorSessionView edited = service.updateElement(M, EDITOR, SESSION, bobId,
                new EditorElementUpdateRequest("representation:main", "form:person", Map.of(NAME, "INVALID")), created.etag());
        assertThat(edited.etag()).isNotEqualTo(created.etag());
        assertThat(edited.session().revision()).isGreaterThan(created.session().revision());
        assertThat(names(edited)).containsExactlyInAnyOrder("INVALID", "INVALID");

        // va: validacion manual completa; hay un diagnostico por cada elemento con nombre INVALID (2, calculado a mano).
        EditorSessionView validated = service.validate(M, EDITOR, SESSION, ValidationRunMode.FULL, edited.etag());
        assertThat(validated.validationReport().mode()).isEqualTo(ValidationRunMode.FULL);
        assertThat(validated.validationReport().diagnostics()).hasSize(2).extracting(ModelValidationDiagnostic::ruleId).containsOnly(RULE);

        // qf: la correccion real se ofrece, se aplica y se revalida de forma incremental.
        String diagnosticId = validated.validationReport().diagnostics().get(0).id();
        assertThat(service.quickFixes(M, EDITOR, SESSION, diagnosticId)).extracting(QuickFixDescriptor::actionId).contains("action:reset");
        EditorSessionQuickFixResult fixed = service.applyQuickFix(M, EDITOR, SESSION, diagnosticId, "action:reset",
                new QuickFixExecutionRequest(Map.of(), List.of()), validated.etag());
        assertThat(fixed.diagnosticResolved()).isTrue();
        assertThat(fixed.view().validationReport().mode()).isEqualTo(ValidationRunMode.INCREMENTAL);
        assertThat(fixed.view().validationReport().diagnostics()).extracting(ModelValidationDiagnostic::id).doesNotContain(diagnosticId);
        assertThat(names(fixed.view())).containsExactlyInAnyOrder("fixed", "INVALID");

        // sv: antes de guardar el disco conserva solo la sesion abierta; guardar y reabrir con otro repositorio la recupera.
        Path sessionFile = temporary.resolve("sessions").resolve(SESSION + ".editor-session.json");
        assertThat(Files.readString(sessionFile)).doesNotContain("fixed").doesNotContain(bobId);
        service.save(M, EDITOR, SESSION, fixed.view().etag());
        String onDisk = Files.readString(sessionFile);
        assertThat(onDisk).contains("fixed").contains(bobId);

        JsonEditorModelSessionRepository freshRepository = new JsonEditorModelSessionRepository(properties, mapper);
        EditorSession reopened = new EditorSession(freshRepository.find(SESSION).orElseThrow().document());
        assertThat(reopened.names()).containsExactlyInAnyOrder("fixed", "INVALID");
        assertThat(reopened.revision()).isEqualTo(fixed.view().session().revision());
    }

    private static List<Object> names(EditorSessionView view) {
        return view.session().model().elements().stream().map(element -> element.attributes().get(NAME)).toList();
    }

    /** Lectura mínima del documento recuperado del disco, para contrastarlo con lo que devolvió el servicio. */
    private record EditorSession(EditorModelSession session) {
        List<Object> names() { return session.model().elements().stream().map((CanonicalModelElement element) -> element.attributes().get(NAME)).toList(); }
        long revision() { return session.revision(); }
    }

    private static ExpressionModel expressionModel() {
        ExpressionDefinition notInvalid = new ExpressionDefinition("expression:name-not-invalid", "expression:name-not-invalid", "", PERSON,
                ExpressionValueType.scalar(ExpressionValueKind.BOOLEAN),
                new BinaryExpressionNode("node:name-not-invalid:cmp", BinaryExpressionOperator.NOT_EQUALS,
                        new AttributeAccessExpressionNode("node:name-not-invalid:name", new SelfExpressionNode("node:name-not-invalid:self"), NAME),
                        new LiteralExpressionNode("node:name-not-invalid:lit", ExpressionValueType.scalar(ExpressionValueKind.STRING), "INVALID")), Map.of());
        return new ExpressionModel("1.0", M, M, List.of(notInvalid), Map.of());
    }

    private static ActionModel actionModel() {
        ActionDefinition reset = new ActionDefinition("action:reset", "Reset name", "", true, List.of(), "",
                List.of(new SetAttributeOperation("operation:reset", new ActionSelector(ActionSelectorKind.SELF, "", ""), NAME, ActionValueSource.literal("fixed"))),
                ActionTransactionPolicy.ATOMIC, ActionUndoPolicy.REQUIRED, Map.of());
        return new ActionModel("1.0", M, M, M, List.of(reset), Map.of());
    }

    private static ValidationModel validationModel() {
        ValidationRule rule = new ValidationRule(RULE, "NAME_NOT_INVALID", "NAME_NOT_INVALID", "", true,
                new ValidationContext(ValidationContextKind.ATTRIBUTE, PERSON, NAME), ValidationSeverity.ERROR, "expression:name-not-invalid",
                "${elementId} has a bad name", List.of(ValidationTrigger.FULL_VALIDATION, ValidationTrigger.ON_ATTRIBUTE_CHANGE),
                List.of(new ValidationDependency(ValidationDependencyKind.ATTRIBUTE, NAME)), List.of("action:reset"), ValidationExecutionMode.INCREMENTAL,
                AuthoritativeRepresentation.CANONICAL, ConversionStatus.FULL, null, Map.of());
        return new ValidationModel("1.0", M, M, M, M,
                List.of(new ValidationRuleSet("set:default", "Default", "", true, 0, List.of(rule.id()), Map.of())), List.of(rule), List.of(), Map.of());
    }
}
