package com.example.editor.validation.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.editor.config.StorageProperties;
import com.example.editor.metamodel.application.CanonicalMetamodelApplicationService;
import com.example.editor.metamodel.canonical.CanonicalMetamodelProperties;
import com.example.editor.metamodel.canonical.JsonCanonicalMetamodelRepository;
import com.example.editor.metamodel.validation.NeutralMetamodelValidator;
import com.example.editor.semantic.expression.ExpressionDependencyAnalyzer;
import com.example.editor.semantic.expression.ExpressionEvaluator;
import com.example.editor.semantic.expression.ExpressionTestFixtures;
import com.example.editor.semantic.expression.ExpressionTypeChecker;
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
import com.example.editor.validation.expression.ExpressionModelApplicationService;
import com.example.editor.validation.expression.ExpressionModelProperties;
import com.example.editor.validation.expression.persistence.JsonExpressionModelRepository;
import com.example.editor.validation.validation.persistence.JsonValidationModelRepository;
import tools.jackson.databind.json.JsonMapper;

/**
 * Evidencia FUERTE real, sin mocks, de guardar de verdad una regla de validacion a traves del mismo
 * servicio de aplicacion real que respalda el boton final «Guardar» del diseñador de validacion.
 * Cubre directamente "ok" (regla activa y verificada) de validation_define_rule: una regla real,
 * con condicion, disparadores y un Quick Fix asociado, se comprueba sin errores y se guarda de
 * verdad en disco a traves de ValidationModelApplicationService.create(), quedando disponible para
 * ejecutarse al releerla desde un repositorio real distinto.
 */
class ValidationModelApplicationServiceSaveIntegrationTests {

    @Test
    void savesARealValidationRuleWithAConditionTriggersAndAQuickFix(@TempDir Path temporary) {
        JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();
        StorageProperties storage = new StorageProperties(temporary.resolve("metamodels"), temporary.resolve("models"),
                temporary.resolve("notations"), temporary.resolve("validations"), temporary.resolve("quickfixes"),
                temporary.resolve("generators"), temporary.resolve("assets"), temporary.resolve("exports"), true);

        CanonicalMetamodelApplicationService metamodelService = new CanonicalMetamodelApplicationService(
                new JsonCanonicalMetamodelRepository(storage, new CanonicalMetamodelProperties(), mapper), new NeutralMetamodelValidator());
        metamodelService.create(ExpressionTestFixtures.metamodel());

        ExpressionTypeChecker typeChecker = new ExpressionTypeChecker(new ExpressionDependencyAnalyzer());
        ExpressionModelApplicationService expressions = new ExpressionModelApplicationService(
                new JsonExpressionModelRepository(storage, new ExpressionModelProperties(), mapper), metamodelService,
                typeChecker, new ExpressionEvaluator(typeChecker));
        expressions.create(ExpressionTestFixtures.METAMODEL_ID, ExpressionTestFixtures.expressionModel(ExpressionTestFixtures.positiveAge()));

        ActionModelApplicationService actions = new ActionModelApplicationService(
                new JsonActionModelRepository(storage, new ActionModelProperties(), mapper),
                new JsonExpressionModelRepository(storage, new ExpressionModelProperties(), mapper),
                metamodelService, new ActionModelChecker(typeChecker));
        actions.create(ExpressionTestFixtures.METAMODEL_ID, actionModel());

        ValidationModelApplicationService service = new ValidationModelApplicationService(
                new JsonValidationModelRepository(storage, new ValidationModelProperties(), mapper),
                new JsonExpressionModelRepository(storage, new ExpressionModelProperties(), mapper),
                new JsonActionModelRepository(storage, new ActionModelProperties(), mapper),
                metamodelService, new ValidationModelChecker(typeChecker));

        ValidationModelSaveResponse response = service.create(ExpressionTestFixtures.METAMODEL_ID, validationModel());

        // "blk"/"sv": la comprobacion real no encuentra problemas de diseño y la regla queda
        // guardada de verdad junto con su conjunto.
        assertThat(response.checkReport().diagnostics()).noneMatch(item -> item.severity() == ValidationModelDiagnosticSeverity.ERROR);
        assertThat(response.validationModel().rules()).singleElement().extracting(ValidationRule::id).isEqualTo("rule:positive-age");

        // "ok": releida desde un repositorio real distinto construido sobre el mismo directorio,
        // la regla esta realmente disponible, con su condicion, sus disparadores y su Quick Fix,
        // lista para ejecutarse en las validaciones.
        ValidationModel reread = new JsonValidationModelRepository(storage, new ValidationModelProperties(), mapper)
                .findById(ExpressionTestFixtures.METAMODEL_ID).orElseThrow().document();
        assertThat(reread.rules()).singleElement().satisfies(rule -> {
            assertThat(rule.severity()).isEqualTo(ValidationSeverity.ERROR);
            assertThat(rule.triggers()).contains(ValidationTrigger.ON_ATTRIBUTE_CHANGE, ValidationTrigger.ON_SAVE);
            assertThat(rule.quickFixIds()).containsExactly("action:set-age-zero");
        });
    }

    private static ActionModel actionModel() {
        ActionDefinition action = new ActionDefinition("action:set-age-zero", "Set age to zero", "", true, List.of(), "",
                List.of(new SetAttributeOperation("operation:set-age", new ActionSelector(ActionSelectorKind.SELF, "", ""), ExpressionTestFixtures.AGE, ActionValueSource.literal(0))),
                ActionTransactionPolicy.ATOMIC, ActionUndoPolicy.REQUIRED, Map.of());
        return new ActionModel("1.0", ExpressionTestFixtures.METAMODEL_ID, ExpressionTestFixtures.METAMODEL_ID, ExpressionTestFixtures.METAMODEL_ID, List.of(action), Map.of());
    }

    private static ValidationModel validationModel() {
        ValidationRule rule = new ValidationRule("rule:positive-age", "Positive age", "POSITIVE_AGE", "", true,
                new ValidationContext(ValidationContextKind.ATTRIBUTE, ExpressionTestFixtures.PERSON, ExpressionTestFixtures.AGE), ValidationSeverity.ERROR,
                ExpressionTestFixtures.positiveAge().id(), "Age must be positive", List.of(ValidationTrigger.ON_ATTRIBUTE_CHANGE, ValidationTrigger.ON_SAVE),
                List.of(new ValidationDependency(ValidationDependencyKind.CLASS, ExpressionTestFixtures.PERSON), new ValidationDependency(ValidationDependencyKind.ATTRIBUTE, ExpressionTestFixtures.AGE)),
                List.of("action:set-age-zero"), ValidationExecutionMode.INCREMENTAL, AuthoritativeRepresentation.CANONICAL, ConversionStatus.FULL, null, Map.of());
        return new ValidationModel("1.0", ExpressionTestFixtures.METAMODEL_ID, ExpressionTestFixtures.METAMODEL_ID, ExpressionTestFixtures.METAMODEL_ID,
                ExpressionTestFixtures.METAMODEL_ID, List.of(new ValidationRuleSet("set:default", "Default", "", true, 0, List.of(rule.id()), Map.of())),
                List.of(rule), List.of(), Map.of());
    }
}
