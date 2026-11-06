package com.example.editor.validation.action;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.example.editor.config.StorageProperties;
import com.example.editor.metamodel.application.CanonicalMetamodelApplicationService;
import com.example.editor.metamodel.canonical.CanonicalMetamodelProperties;
import com.example.editor.metamodel.canonical.JsonCanonicalMetamodelRepository;
import com.example.editor.metamodel.validation.NeutralMetamodelValidator;
import com.example.editor.semantic.expression.AttributeAccessExpressionNode;
import com.example.editor.semantic.expression.ExpressionDefinition;
import com.example.editor.semantic.expression.ExpressionDependencyAnalyzer;
import com.example.editor.semantic.expression.ExpressionEvaluator;
import com.example.editor.semantic.expression.ExpressionTestFixtures;
import com.example.editor.semantic.expression.ExpressionTypeChecker;
import com.example.editor.semantic.expression.ExpressionValueKind;
import com.example.editor.semantic.expression.ExpressionValueType;
import com.example.editor.semantic.expression.SelfExpressionNode;
import com.example.editor.validation.action.persistence.JsonActionModelRepository;
import com.example.editor.validation.action.web.CanonicalActionController;
import com.example.editor.validation.expression.ExpressionModel;
import com.example.editor.validation.expression.ExpressionModelApplicationService;
import com.example.editor.validation.expression.ExpressionModelProperties;
import com.example.editor.validation.expression.persistence.JsonExpressionModelRepository;
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
import com.example.editor.validation.validation.ValidationModelDiagnostic;
import com.example.editor.validation.validation.ValidationModelDiagnosticSeverity;
import com.example.editor.validation.validation.ValidationModelProperties;
import com.example.editor.validation.validation.ValidationModelSaveResponse;
import com.example.editor.validation.validation.ValidationRule;
import com.example.editor.validation.validation.ValidationRuleSet;
import com.example.editor.validation.validation.ValidationSeverity;
import com.example.editor.validation.validation.ValidationTrigger;
import com.example.editor.validation.validation.persistence.JsonValidationModelRepository;
import com.example.editor.web.ApiExceptionHandler;
import tools.jackson.databind.json.JsonMapper;

/**
 * Evidencia real de la definicion de una Quick Fix (ActionModel): validation_define_quickfix.
 * {id,pol,pre,par,op,lnk,chk}. MockMvc standalone sobre CanonicalActionController +
 * ActionModelApplicationService + ActionModelChecker + repositorios JSON reales en @TempDir y
 * ValidationModelApplicationService real. El oraculo de persistencia es el JSON escrito en disco,
 * leido directamente, y la reapertura del repositorio desde cero.
 */
class QuickFixDefinitionPersistenceAndCheckIntegrationTests {

    private static final String M = ExpressionTestFixtures.METAMODEL_ID;
    private static final String BOOLEAN_EXPRESSION = "expression:positive-age";
    private static final String NUMERIC_EXPRESSION = "expression:age-value";
    private static final String MAIN = "action:normalize-age";
    private static final String SECONDARY = "action:note-name";

    @TempDir
    Path temporary;

    private record Fixture(StorageProperties storage, JsonMapper mapper, MockMvc mockMvc, ValidationModelApplicationService validations) { }

    /** id + pol + pre + par + op: se define, se persiste y se reabre desde disco una accion completa. */
    @Test
    void definesPersistsAndReopensAnActionWithPoliciesPreconditionParametersAndOperations() throws Exception {
        Fixture fixture = fixture("define");
        fixture.mockMvc().perform(post("/api/canonical-metamodels/" + M + "/actions").contentType(MediaType.APPLICATION_JSON)
                        .content(fixture.mapper().writeValueAsString(fullModel())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.checkReport.diagnostics", empty()))
                .andExpect(jsonPath("$.checkReport.actions[*].diagnostics[*]", empty()));

        fixture.mockMvc().perform(get("/api/canonical-metamodels/" + M + "/actions")).andExpect(status().isOk())
                .andExpect(jsonPath("$.actions[0].id").value(MAIN))
                .andExpect(jsonPath("$.actions[0].name").value("Normalize age"))
                .andExpect(jsonPath("$.actions[0].description").value("Sets a valid age and tidies the name"))
                .andExpect(jsonPath("$.actions[0].transactionPolicy").value("ATOMIC"))
                .andExpect(jsonPath("$.actions[0].undoPolicy").value("REQUIRED"))
                .andExpect(jsonPath("$.actions[0].preconditionExpressionId").value(BOOLEAN_EXPRESSION))
                .andExpect(jsonPath("$.actions[0].parameters[0].name").value("newAge"))
                .andExpect(jsonPath("$.actions[0].operations", hasSize(5)))
                .andExpect(jsonPath("$.actions[1].transactionPolicy").value("BEST_EFFORT"))
                .andExpect(jsonPath("$.actions[1].undoPolicy").value("NOT_SUPPORTED"));
        fixture.mockMvc().perform(post("/api/canonical-metamodels/" + M + "/actions/check")).andExpect(status().isOk())
                .andExpect(jsonPath("$.actionModelId").value(M));

        // Oraculo independiente: el fichero JSON que HYDRA dejo en disco.
        String json = actionFileContent(fixture.storage()).replaceAll("\\s", "");
        assertThat(json).contains("\"id\":\"" + MAIN + "\"").contains("\"transactionPolicy\":\"ATOMIC\"")
                .contains("\"transactionPolicy\":\"BEST_EFFORT\"").contains("\"undoPolicy\":\"NOT_SUPPORTED\"")
                .contains("\"preconditionExpressionId\":\"" + BOOLEAN_EXPRESSION + "\"").contains("\"name\":\"newAge\"");

        // Reabrir el repositorio desde cero (otro objeto, mismo directorio).
        ActionModel reopened = new JsonActionModelRepository(fixture.storage(), new ActionModelProperties(), fixture.mapper())
                .findById(M).orElseThrow().document();
        assertThat(reopened.actions()).extracting(ActionDefinition::id).containsExactly(MAIN, SECONDARY);
        ActionDefinition main = reopened.actions().get(0);
        assertThat(main.parameters()).singleElement().satisfies(parameter -> {
            assertThat(parameter.name()).isEqualTo("newAge");
            assertThat(parameter.type()).isEqualTo(ActionParameterType.INTEGER);
            assertThat(parameter.required()).isTrue();
        });
        assertThat(main.operations()).extracting(operation -> operation.getClass().getSimpleName()).containsExactly("SetAttributeOperation",
                "ClearAttributeOperation", "TransformTextOperation", "CopyAttributeOperation", "ExecuteActionOperation");
    }

    /** chk: precondicion inexistente o no booleana, recursion directa y accion inexistente. */
    @Test
    void checkerRejectsMissingOrNonBooleanPreconditionsRecursionAndUnknownActions() throws Exception {
        expectCode("precondition-missing", action("action:a", "expression:missing", List.of(set("operation:a"))), "UNKNOWN_PRECONDITION_EXPRESSION");
        expectCode("precondition-type", action("action:a", NUMERIC_EXPRESSION, List.of(set("operation:a"))), "PRECONDITION_NOT_BOOLEAN");
        expectCode("recursion", action("action:a", "", List.of(new ExecuteActionOperation("operation:self", "action:a", Map.of()))), "DIRECT_ACTION_RECURSION");
        expectCode("unknown-action", action("action:a", "", List.of(new ExecuteActionOperation("operation:ghost", "action:ghost", Map.of()))), "UNKNOWN_ACTION");
    }

    /** chk: una accion sin operaciones solo existe como Quick Fix importada, desactivada y de solo revision. */
    @Test
    void actionWithoutOperationsIsOnlyAllowedAsDisabledReviewOnly() throws Exception {
        assertThatThrownBy(() -> new ActionDefinition("action:empty", "Empty", "", true, List.of(), "", List.of(),
                ActionTransactionPolicy.ATOMIC, ActionUndoPolicy.REQUIRED, Map.of()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("operations must not be empty");
        assertThatThrownBy(() -> new ActionDefinition("action:empty", "Empty", "", true, List.of(), "", List.of(),
                ActionTransactionPolicy.ATOMIC, ActionUndoPolicy.REQUIRED, Map.of("hydra.import.reviewOnly", "true")))
                .isInstanceOf(IllegalArgumentException.class);

        ActionDefinition reviewOnly = new ActionDefinition("action:review", "Review manually", "", false, List.of(), "", List.of(),
                ActionTransactionPolicy.BEST_EFFORT, ActionUndoPolicy.NOT_SUPPORTED, Map.of("hydra.import.reviewOnly", "true"));
        Fixture fixture = fixture("review-only");
        fixture.mockMvc().perform(post("/api/canonical-metamodels/" + M + "/actions").contentType(MediaType.APPLICATION_JSON)
                        .content(fixture.mapper().writeValueAsString(new ActionModel("1.0", M, M, M, List.of(reviewOnly), Map.of()))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.checkReport.actions[*].diagnostics[*].code", empty()));
        ActionDefinition reopened = new JsonActionModelRepository(fixture.storage(), new ActionModelProperties(), fixture.mapper())
                .findById(M).orElseThrow().document().actions().getFirst();
        assertThat(reopened.enabled()).isFalse();
        assertThat(reopened.operations()).isEmpty();
        assertThat(reopened.annotations()).containsEntry("hydra.import.reviewOnly", "true");
    }

    /** lnk: una regla con quickFixIds duplicados se senala, y una regla valida enlazada a la accion se guarda y se relee. */
    @Test
    void ruleLinkedToTheActionIsAcceptedAndDuplicatedLinksAreRejected() throws Exception {
        Fixture duplicated = fixture("link-duplicated");
        persistActions(duplicated, fullModel());
        ValidationModelSaveResponse bad = duplicated.validations().create(M, validationModel(List.of(MAIN, MAIN)));
        assertThat(errorCodes(bad)).contains("DUPLICATE_QUICK_FIX");

        Fixture valid = fixture("link-valid");
        persistActions(valid, fullModel());
        ValidationModelSaveResponse good = valid.validations().create(M, validationModel(List.of(MAIN)));
        assertThat(errorCodes(good)).isEmpty();
        ValidationModel reread = new JsonValidationModelRepository(valid.storage(), new ValidationModelProperties(), valid.mapper())
                .findById(M).orElseThrow().document();
        assertThat(reread.rules()).singleElement().satisfies(rule -> assertThat(rule.quickFixIds()).containsExactly(MAIN));
        // La accion enlazada existe de verdad en el repositorio releido desde disco.
        assertThat(new JsonActionModelRepository(valid.storage(), new ActionModelProperties(), valid.mapper()).findById(M).orElseThrow()
                .document().findAction(MAIN)).isPresent();
    }

    // ---- helpers ----

    private void expectCode(String name, ActionDefinition action, String code) throws Exception {
        Fixture fixture = fixture(name);
        fixture.mockMvc().perform(post("/api/canonical-metamodels/" + M + "/actions").contentType(MediaType.APPLICATION_JSON)
                        .content(fixture.mapper().writeValueAsString(new ActionModel("1.0", M, M, M, List.of(action), Map.of()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.checkReport.actions[*].diagnostics[*].code", hasItem(code)));
    }

    private void persistActions(Fixture fixture, ActionModel model) throws Exception {
        fixture.mockMvc().perform(post("/api/canonical-metamodels/" + M + "/actions").contentType(MediaType.APPLICATION_JSON)
                .content(fixture.mapper().writeValueAsString(model))).andExpect(status().isCreated());
    }

    private static List<String> errorCodes(ValidationModelSaveResponse response) {
        return Stream.concat(response.checkReport().diagnostics().stream(),
                        response.checkReport().rules().stream().flatMap(rule -> rule.diagnostics().stream()))
                .filter(diagnostic -> diagnostic.severity() == ValidationModelDiagnosticSeverity.ERROR)
                .map(ValidationModelDiagnostic::code).toList();
    }

    private static String actionFileContent(StorageProperties storage) throws Exception {
        try (Stream<Path> files = Files.list(storage.validations())) {
            for(Path file : files.filter(path -> path.getFileName().toString().endsWith(".json")).toList()) {
                String content = Files.readString(file, StandardCharsets.UTF_8);
                if(content.contains("\"actions\"")) return content;
            }
        }
        throw new AssertionError("No action document was written to " + storage.validations());
    }

    private Fixture fixture(String name) {
        Path root = temporary.resolve(name);
        JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();
        StorageProperties storage = new StorageProperties(root.resolve("metamodels"), root.resolve("models"), root.resolve("notations"),
                root.resolve("validations"), root.resolve("quickfixes"), root.resolve("generators"), root.resolve("assets"), root.resolve("exports"), true);
        CanonicalMetamodelApplicationService metamodelService = new CanonicalMetamodelApplicationService(
                new JsonCanonicalMetamodelRepository(storage, new CanonicalMetamodelProperties(), mapper), new NeutralMetamodelValidator());
        metamodelService.create(ExpressionTestFixtures.metamodel());
        ExpressionTypeChecker typeChecker = new ExpressionTypeChecker(new ExpressionDependencyAnalyzer());
        JsonExpressionModelRepository expressionRepository = new JsonExpressionModelRepository(storage, new ExpressionModelProperties(), mapper);
        ExpressionDefinition numeric = new ExpressionDefinition(NUMERIC_EXPRESSION, "Age value", "", ExpressionTestFixtures.PERSON,
                ExpressionValueType.scalar(ExpressionValueKind.INTEGER),
                new AttributeAccessExpressionNode("node:age-value", new SelfExpressionNode("node:age-value:self"), ExpressionTestFixtures.AGE), Map.of());
        ExpressionModel expressionModel = ExpressionTestFixtures.expressionModel(ExpressionTestFixtures.positiveAge(), numeric);
        new ExpressionModelApplicationService(expressionRepository, metamodelService, typeChecker, new ExpressionEvaluator(typeChecker)).create(M, expressionModel);
        JsonActionModelRepository actionRepository = new JsonActionModelRepository(storage, new ActionModelProperties(), mapper);
        ActionModelApplicationService actions = new ActionModelApplicationService(actionRepository, expressionRepository, metamodelService,
                new ActionModelChecker(typeChecker));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new CanonicalActionController(actions)).setControllerAdvice(new ApiExceptionHandler()).build();
        ValidationModelApplicationService validations = new ValidationModelApplicationService(
                new JsonValidationModelRepository(storage, new ValidationModelProperties(), mapper), expressionRepository, actionRepository,
                metamodelService, new ValidationModelChecker(typeChecker));
        return new Fixture(storage, mapper, mockMvc, validations);
    }

    private static ActionSelector self() {
        return new ActionSelector(ActionSelectorKind.SELF, "", "");
    }

    private static SetAttributeOperation set(String id) {
        return new SetAttributeOperation(id, self(), ExpressionTestFixtures.AGE, ActionValueSource.literal(0));
    }

    private static ActionDefinition action(String id, String precondition, List<ActionOperation> operations) {
        return new ActionDefinition(id, "Action " + id, "", true, List.of(), precondition, operations, ActionTransactionPolicy.ATOMIC, ActionUndoPolicy.REQUIRED, Map.of());
    }

    private static ActionModel fullModel() {
        ActionDefinition main = new ActionDefinition(MAIN, "Normalize age", "Sets a valid age and tidies the name", true,
                List.of(new ActionParameter("newAge", ActionParameterType.INTEGER, true)), BOOLEAN_EXPRESSION,
                List.of(new SetAttributeOperation("operation:set-age", self(), ExpressionTestFixtures.AGE, ActionValueSource.parameter("newAge")),
                        new ClearAttributeOperation("operation:clear-name", self(), ExpressionTestFixtures.NAME),
                        new TransformTextOperation("operation:upper-name", self(), ExpressionTestFixtures.NAME, TextTransformation.UPPERCASE),
                        new CopyAttributeOperation("operation:copy-name", self(), ExpressionTestFixtures.NAME, self(), ExpressionTestFixtures.NAME),
                        new ExecuteActionOperation("operation:note", SECONDARY, Map.of())),
                ActionTransactionPolicy.ATOMIC, ActionUndoPolicy.REQUIRED, Map.of());
        ActionDefinition secondary = new ActionDefinition(SECONDARY, "Note name", "", true, List.of(), "",
                List.of(new SetAttributeOperation("operation:note-name", self(), ExpressionTestFixtures.NAME, ActionValueSource.literal("noted"))),
                ActionTransactionPolicy.BEST_EFFORT, ActionUndoPolicy.NOT_SUPPORTED, Map.of());
        return new ActionModel("1.0", M, M, M, List.of(main, secondary), Map.of());
    }

    private static ValidationModel validationModel(List<String> fixes) {
        ValidationRule rule = new ValidationRule("rule:positive-age", "Positive age", "POSITIVE_AGE", "", true,
                new ValidationContext(ValidationContextKind.ATTRIBUTE, ExpressionTestFixtures.PERSON, ExpressionTestFixtures.AGE), ValidationSeverity.ERROR,
                BOOLEAN_EXPRESSION, "Age must be positive", List.of(ValidationTrigger.ON_ATTRIBUTE_CHANGE, ValidationTrigger.ON_SAVE),
                List.of(new ValidationDependency(ValidationDependencyKind.CLASS, ExpressionTestFixtures.PERSON),
                        new ValidationDependency(ValidationDependencyKind.ATTRIBUTE, ExpressionTestFixtures.AGE)),
                fixes, ValidationExecutionMode.INCREMENTAL, AuthoritativeRepresentation.CANONICAL, ConversionStatus.FULL, null, Map.of());
        return new ValidationModel("1.0", M, M, M, M,
                List.of(new ValidationRuleSet("set:default", "Default", "", true, 0, List.of(rule.id()), Map.of())), List.of(rule), List.of(), Map.of());
    }
}
