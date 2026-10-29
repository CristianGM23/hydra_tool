package com.example.editor.validation.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
import com.example.editor.validation.action.SetAttributeOperation;
import com.example.editor.validation.action.persistence.JsonActionModelRepository;
import com.example.editor.validation.expression.ExpressionModel;
import com.example.editor.validation.expression.ExpressionModelApplicationService;
import com.example.editor.validation.expression.ExpressionModelProperties;
import com.example.editor.validation.expression.persistence.JsonExpressionModelRepository;
import com.example.editor.validation.validation.persistence.JsonValidationModelRepository;
import tools.jackson.databind.json.JsonMapper;

/**
 * Evidencia real, sin mocks, de las puertas de rechazo al definir una regla de validacion:
 * validation_define_rule.{ctx,trg,cnd,qf,sv,chk}. Los repositorios JSON (metamodelo, expresiones,
 * acciones, validaciones) viven en @TempDir; cada caso se guarda a traves de
 * ValidationModelApplicationService.create y se lee el informe del comprobador real. El oraculo de
 * persistencia es el fichero JSON que queda (o no) en disco.
 */
class ValidationRuleDefinitionRejectionsIntegrationTests {

    private static final String M = ExpressionTestFixtures.METAMODEL_ID;
    private static final String AGE_EXPRESSION = "expression:positive-age";
    private static final String NOT_BOOLEAN_EXPRESSION = "expression:age-value";
    private static final String FIX = "action:set-age-zero";

    @TempDir
    Path temporary;

    private record Fixture(StorageProperties storage, JsonMapper mapper, ValidationModelApplicationService validations) { }

    /** ctx: contexto inconsistente con el metamodelo real. */
    @Test
    void rejectsContextsThatDoNotMatchTheRealMetamodel() {
        Fixture classWithoutClass = fixture("ctx-class", true, true);
        assertThat(errorCodes(classWithoutClass.validations().create(M, model(rule("rule:ctx-class", "CTX_CLASS",
                new ValidationContext(ValidationContextKind.CLASS, "", ""), AGE_EXPRESSION, standardTriggers(), List.of(), ValidationExecutionMode.INCREMENTAL)))))
                .contains("CONTEXT_CLASS_REQUIRED");

        Fixture foreignAttribute = fixture("ctx-attribute", true, true);
        assertThat(errorCodes(foreignAttribute.validations().create(M, model(rule("rule:ctx-attribute", "CTX_ATTRIBUTE",
                new ValidationContext(ValidationContextKind.ATTRIBUTE, ExpressionTestFixtures.COMPANY, ExpressionTestFixtures.AGE), AGE_EXPRESSION,
                standardTriggers(), List.of(), ValidationExecutionMode.INCREMENTAL)))))
                .contains("CONTEXT_FEATURE_OWNER_MISMATCH");

        Fixture foreignRelation = fixture("ctx-relation", true, true);
        assertThat(errorCodes(foreignRelation.validations().create(M, model(rule("rule:ctx-relation", "CTX_RELATION",
                new ValidationContext(ValidationContextKind.RELATION, ExpressionTestFixtures.PERSON, ExpressionTestFixtures.EMPLOYEES), AGE_EXPRESSION,
                standardTriggers(), List.of(), ValidationExecutionMode.INCREMENTAL)))))
                .contains("CONTEXT_FEATURE_OWNER_MISMATCH");
    }

    /** trg: disparadores incompatibles con los modos manual y completo, o ausentes. */
    @Test
    void rejectsTriggersThatContradictTheExecutionMode() {
        assertThat(errorCodes(fixture("trg-manual", true, true).validations().create(M, model(rule("rule:trg-manual", "TRG_MANUAL", ageContext(),
                AGE_EXPRESSION, List.of(ValidationTrigger.ON_SAVE), List.of(), ValidationExecutionMode.MANUAL_ONLY)))))
                .contains("MANUAL_RULE_TRIGGER_MISMATCH");
        assertThat(errorCodes(fixture("trg-full", true, true).validations().create(M, model(rule("rule:trg-full", "TRG_FULL", ageContext(),
                AGE_EXPRESSION, List.of(ValidationTrigger.ON_ATTRIBUTE_CHANGE), List.of(), ValidationExecutionMode.FULL_ONLY)))))
                .contains("FULL_RULE_TRIGGER_MISMATCH");
        assertThat(errorCodes(fixture("trg-none", true, true).validations().create(M, model(rule("rule:trg-none", "TRG_NONE", ageContext(),
                AGE_EXPRESSION, List.of(), List.of(), ValidationExecutionMode.INCREMENTAL)))))
                .contains("TRIGGER_REQUIRED");
    }

    /** cnd: condicion con expresion inexistente o de tipo no booleano. */
    @Test
    void rejectsConditionsThatAreMissingOrNotBoolean() {
        assertThat(errorCodes(fixture("cnd-missing", true, true).validations().create(M, model(rule("rule:cnd-missing", "CND_MISSING", ageContext(),
                "expression:does-not-exist", standardTriggers(), List.of(), ValidationExecutionMode.INCREMENTAL)))))
                .contains("UNKNOWN_CONDITION_EXPRESSION");
        assertThat(errorCodes(fixture("cnd-type", true, true).validations().create(M, model(rule("rule:cnd-type", "CND_TYPE", ageContext(),
                NOT_BOOLEAN_EXPRESSION, standardTriggers(), List.of(), ValidationExecutionMode.INCREMENTAL)))))
                .contains("CONDITION_NOT_BOOLEAN");
    }

    /** qf: Quick Fix duplicado o inexistente. */
    @Test
    void rejectsDuplicatedOrUnknownQuickFixes() {
        assertThat(errorCodes(fixture("qf-dup", true, true).validations().create(M, model(rule("rule:qf-dup", "QF_DUP", ageContext(),
                AGE_EXPRESSION, standardTriggers(), List.of(FIX, FIX), ValidationExecutionMode.INCREMENTAL)))))
                .contains("DUPLICATE_QUICK_FIX");
        assertThat(errorCodes(fixture("qf-unknown", true, true).validations().create(M, model(rule("rule:qf-unknown", "QF_UNKNOWN", ageContext(),
                AGE_EXPRESSION, standardTriggers(), List.of("action:missing"), ValidationExecutionMode.INCREMENTAL)))))
                .contains("UNKNOWN_QUICK_FIX");
    }

    /** sv + chk: documentos guardados en orden incorrecto no se escriben en disco. */
    @Test
    void refusesToSaveValidationsBeforeTheirExpressionOrActionDocuments() {
        Fixture noExpressions = fixture("order-expression", false, false);
        assertThatThrownBy(() -> noExpressions.validations().create(M, model(validRule())))
                .isInstanceOf(ValidationModelApplicationException.class)
                .hasMessageContaining("Expression model must be created before the validation model");
        assertThat(new JsonValidationModelRepository(noExpressions.storage(), new ValidationModelProperties(), noExpressions.mapper()).findById(M)).isEmpty();

        Fixture noActions = fixture("order-action", true, false);
        assertThatThrownBy(() -> noActions.validations().create(M, model(validRule())))
                .isInstanceOf(ValidationModelApplicationException.class)
                .hasMessageContaining("Configured action model was not found");
        assertThat(new JsonValidationModelRepository(noActions.storage(), new ValidationModelProperties(), noActions.mapper()).findById(M)).isEmpty();
    }

    /** sv + chk: una regla valida pasa el comprobador sin errores y queda en disco. */
    @Test
    void savesAValidRuleAfterTheRejections() throws Exception {
        Fixture fixture = fixture("valid", true, true);
        ValidationModelSaveResponse response = fixture.validations().create(M, model(validRule()));

        assertThat(errorCodes(response)).isEmpty();
        assertThat(response.validationModel().rules()).singleElement().extracting(ValidationRule::id).isEqualTo("rule:valid");

        String json;
        try (Stream<Path> files = Files.list(fixture.storage().validations())) {
            // acciones y validaciones comparten directorio: se elige el documento que contiene las reglas.
            json = files.filter(path -> path.getFileName().toString().endsWith(".json")).map(path -> {
                try { return Files.readString(path, StandardCharsets.UTF_8); }
                catch(java.io.IOException exception) { throw new java.io.UncheckedIOException(exception); }
            }).filter(content -> content.contains("\"rules\"")).findFirst().orElseThrow();
        }
        assertThat(json).contains("rule:valid").contains("action:set-age-zero").contains(AGE_EXPRESSION);
        ValidationModel reread = new JsonValidationModelRepository(fixture.storage(), new ValidationModelProperties(), fixture.mapper())
                .findById(M).orElseThrow().document();
        assertThat(reread.rules()).singleElement().satisfies(rule -> {
            assertThat(rule.quickFixIds()).containsExactly(FIX);
            assertThat(rule.triggers()).containsExactly(ValidationTrigger.ON_ATTRIBUTE_CHANGE, ValidationTrigger.ON_SAVE);
        });
    }

    // ---- helpers ----

    private List<String> errorCodes(ValidationModelSaveResponse response) {
        ValidationModelCheckReport report = response.checkReport();
        return Stream.concat(report.diagnostics().stream(), report.rules().stream().flatMap(rule -> rule.diagnostics().stream()))
                .filter(diagnostic -> diagnostic.severity() == ValidationModelDiagnosticSeverity.ERROR)
                .map(ValidationModelDiagnostic::code).toList();
    }

    private Fixture fixture(String name, boolean withExpressions, boolean withActions) {
        Path root = temporary.resolve(name);
        JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();
        StorageProperties storage = new StorageProperties(root.resolve("metamodels"), root.resolve("models"), root.resolve("notations"),
                root.resolve("validations"), root.resolve("quickfixes"), root.resolve("generators"), root.resolve("assets"), root.resolve("exports"), true);
        CanonicalMetamodelApplicationService metamodelService = new CanonicalMetamodelApplicationService(
                new JsonCanonicalMetamodelRepository(storage, new CanonicalMetamodelProperties(), mapper), new NeutralMetamodelValidator());
        metamodelService.create(ExpressionTestFixtures.metamodel());
        ExpressionTypeChecker typeChecker = new ExpressionTypeChecker(new ExpressionDependencyAnalyzer());
        JsonExpressionModelRepository expressionRepository = new JsonExpressionModelRepository(storage, new ExpressionModelProperties(), mapper);
        if(withExpressions) {
            new ExpressionModelApplicationService(expressionRepository, metamodelService, typeChecker, new ExpressionEvaluator(typeChecker))
                    .create(M, expressionModel());
        }
        JsonActionModelRepository actionRepository = new JsonActionModelRepository(storage, new ActionModelProperties(), mapper);
        if(withActions) {
            new ActionModelApplicationService(actionRepository, expressionRepository, metamodelService, new ActionModelChecker(typeChecker))
                    .create(M, actionModel());
        }
        ValidationModelApplicationService validations = new ValidationModelApplicationService(
                new JsonValidationModelRepository(storage, new ValidationModelProperties(), mapper), expressionRepository, actionRepository,
                metamodelService, new ValidationModelChecker(typeChecker));
        return new Fixture(storage, mapper, validations);
    }

    private static ExpressionModel expressionModel() {
        ExpressionDefinition notBoolean = new ExpressionDefinition(NOT_BOOLEAN_EXPRESSION, "Age value", "", ExpressionTestFixtures.PERSON,
                ExpressionValueType.scalar(ExpressionValueKind.INTEGER),
                new AttributeAccessExpressionNode("node:age-value", new SelfExpressionNode("node:age-value:self"), ExpressionTestFixtures.AGE), Map.of());
        return ExpressionTestFixtures.expressionModel(ExpressionTestFixtures.positiveAge(), notBoolean);
    }

    private static ActionModel actionModel() {
        ActionDefinition action = new ActionDefinition(FIX, "Set age to zero", "", true, List.of(), "",
                List.of(new SetAttributeOperation("operation:set-age", new ActionSelector(ActionSelectorKind.SELF, "", ""), ExpressionTestFixtures.AGE,
                        ActionValueSource.literal(0))), ActionTransactionPolicy.ATOMIC, ActionUndoPolicy.REQUIRED, Map.of());
        return new ActionModel("1.0", M, M, M, List.of(action), Map.of());
    }

    private static ValidationContext ageContext() {
        return new ValidationContext(ValidationContextKind.ATTRIBUTE, ExpressionTestFixtures.PERSON, ExpressionTestFixtures.AGE);
    }

    private static List<ValidationTrigger> standardTriggers() {
        return List.of(ValidationTrigger.ON_ATTRIBUTE_CHANGE, ValidationTrigger.ON_SAVE);
    }

    private static ValidationRule validRule() {
        return rule("rule:valid", "VALID", ageContext(), AGE_EXPRESSION, standardTriggers(), List.of(FIX), ValidationExecutionMode.INCREMENTAL);
    }

    private static ValidationRule rule(String id, String code, ValidationContext context, String expressionId, List<ValidationTrigger> triggers,
            List<String> fixes, ValidationExecutionMode mode) {
        return new ValidationRule(id, "Rule " + code, code, "", true, context, ValidationSeverity.ERROR, expressionId, "Age must be positive",
                triggers, List.of(new ValidationDependency(ValidationDependencyKind.CLASS, ExpressionTestFixtures.PERSON),
                        new ValidationDependency(ValidationDependencyKind.ATTRIBUTE, ExpressionTestFixtures.AGE)),
                fixes, mode, AuthoritativeRepresentation.CANONICAL, ConversionStatus.FULL, null, Map.of());
    }

    private static ValidationModel model(ValidationRule rule) {
        return new ValidationModel("1.0", M, M, M, M,
                List.of(new ValidationRuleSet("set:default", "Default", "", true, 0, List.of(rule.id()), Map.of())), List.of(rule), List.of(), Map.of());
    }
}
