package com.example.editor.validation.validation;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.example.editor.validation.action.ActionDefinition;
import com.example.editor.validation.action.ActionModel;
import com.example.editor.validation.action.ActionSelector;
import com.example.editor.validation.action.ActionSelectorKind;
import com.example.editor.validation.action.ActionTransactionPolicy;
import com.example.editor.validation.action.ActionUndoPolicy;
import com.example.editor.validation.action.ActionValueSource;
import com.example.editor.validation.action.SetAttributeOperation;
import com.example.editor.semantic.expression.ExpressionDefinition;
import com.example.editor.semantic.expression.ExpressionDependencyAnalyzer;
import com.example.editor.semantic.expression.ExpressionTestFixtures;
import com.example.editor.semantic.expression.ExpressionTypeChecker;
import com.example.editor.semantic.expression.ExpressionValueKind;
import com.example.editor.semantic.expression.ExpressionValueType;
import com.example.editor.semantic.expression.LiteralExpressionNode;
import static org.assertj.core.api.Assertions.assertThat;

class ValidationModelCheckerTests {
    private ValidationModelChecker checker;
    @BeforeEach void setUp() { checker = new ValidationModelChecker(new ExpressionTypeChecker(new ExpressionDependencyAnalyzer())); }

    @Test void acceptsAValidationLinkedToAQuickFix() {
        ValidationModelCheckReport report = checker.check(model(rule()), ExpressionTestFixtures.metamodel(),
                ExpressionTestFixtures.expressionModel(ExpressionTestFixtures.positiveAge()), actions());
        assertThat(report.hasErrors()).isFalse();
        assertThat(report.rules()).singleElement().satisfies(result -> {
            assertThat(result.diagnostics()).extracting(ValidationModelDiagnostic::severity).doesNotContain(ValidationModelDiagnosticSeverity.ERROR);
            assertThat(result.inferredDependencies()).contains(new ValidationDependency(ValidationDependencyKind.ATTRIBUTE, ExpressionTestFixtures.AGE));
        });
    }

    @Test void rejectsNonBooleanConditionsAndMissingQuickFixes() {
        ExpressionDefinition numeric = new ExpressionDefinition("expression:numeric", "Numeric", "", ExpressionTestFixtures.PERSON, null,
                new LiteralExpressionNode("node:number", ExpressionValueType.scalar(ExpressionValueKind.INTEGER), 1), Map.of());
        ValidationRule invalid = new ValidationRule("rule:invalid", "Invalid", "INVALID", "", true,
                new ValidationContext(ValidationContextKind.ATTRIBUTE, ExpressionTestFixtures.PERSON, ExpressionTestFixtures.AGE), ValidationSeverity.ERROR,
                numeric.id(), "Invalid", List.of(ValidationTrigger.ON_SAVE), List.of(), List.of("action:missing"), ValidationExecutionMode.INCREMENTAL,
                AuthoritativeRepresentation.CANONICAL, ConversionStatus.FULL, null, Map.of());
        ValidationModelCheckReport report = checker.check(model(invalid), ExpressionTestFixtures.metamodel(), ExpressionTestFixtures.expressionModel(numeric), actions());
        assertThat(report.rules().getFirst().diagnostics()).extracting(ValidationModelDiagnostic::code).contains("CONDITION_NOT_BOOLEAN", "UNKNOWN_QUICK_FIX");
    }

    @Test void detectsContextFeatureMismatches() {
        ValidationRule invalid = new ValidationRule("rule:invalid", "Invalid", "INVALID", "", true,
                new ValidationContext(ValidationContextKind.ATTRIBUTE, ExpressionTestFixtures.COMPANY, ExpressionTestFixtures.AGE), ValidationSeverity.ERROR,
                ExpressionTestFixtures.positiveAge().id(), "Invalid", List.of(ValidationTrigger.ON_SAVE), List.of(), List.of(), ValidationExecutionMode.INCREMENTAL,
                AuthoritativeRepresentation.CANONICAL, ConversionStatus.FULL, null, Map.of());
        ValidationModelCheckReport report = checker.check(model(invalid), ExpressionTestFixtures.metamodel(),
                ExpressionTestFixtures.expressionModel(ExpressionTestFixtures.positiveAge()), actions());
        assertThat(report.rules().getFirst().diagnostics()).extracting(ValidationModelDiagnostic::code).contains("CONTEXT_FEATURE_OWNER_MISMATCH", "CONTEXT_EXPRESSION_MISMATCH");
    }

    @Test void reportsMissingAndUnusedDependencies() {
        ValidationRule changed = new ValidationRule("rule:positive-age", "Positive age", "POSITIVE_AGE", "", true,
                new ValidationContext(ValidationContextKind.ATTRIBUTE, ExpressionTestFixtures.PERSON, ExpressionTestFixtures.AGE), ValidationSeverity.ERROR,
                ExpressionTestFixtures.positiveAge().id(), "Age must be positive", List.of(ValidationTrigger.ON_SAVE),
                List.of(new ValidationDependency(ValidationDependencyKind.CLASS, ExpressionTestFixtures.COMPANY)), List.of(), ValidationExecutionMode.INCREMENTAL,
                AuthoritativeRepresentation.CANONICAL, ConversionStatus.FULL, null, Map.of());
        ValidationModelCheckReport report = checker.check(model(changed), ExpressionTestFixtures.metamodel(),
                ExpressionTestFixtures.expressionModel(ExpressionTestFixtures.positiveAge()), actions());
        assertThat(report.rules().getFirst().diagnostics()).extracting(ValidationModelDiagnostic::code)
                .contains("MISSING_DECLARED_DEPENDENCY", "UNUSED_DECLARED_DEPENDENCY");
    }

    @Test void acceptsAnInheritedAttributeAsAValidationContext() {
        ValidationRule inherited = new ValidationRule("rule:employee-age", "Employee age", "EMPLOYEE_AGE", "", true,
                new ValidationContext(ValidationContextKind.ATTRIBUTE, ExpressionTestFixtures.EMPLOYEE, ExpressionTestFixtures.AGE), ValidationSeverity.ERROR,
                ExpressionTestFixtures.positiveAge().id(), "Age must be positive", List.of(ValidationTrigger.ON_SAVE),
                List.of(new ValidationDependency(ValidationDependencyKind.ATTRIBUTE, ExpressionTestFixtures.AGE)), List.of(), ValidationExecutionMode.INCREMENTAL,
                AuthoritativeRepresentation.CANONICAL, ConversionStatus.FULL, null, Map.of());
        ValidationModelCheckReport report = checker.check(model(inherited), ExpressionTestFixtures.metamodel(),
                ExpressionTestFixtures.expressionModel(ExpressionTestFixtures.positiveAge()), actions());
        assertThat(report.rules().getFirst().diagnostics()).extracting(ValidationModelDiagnostic::code)
                .doesNotContain("CONTEXT_FEATURE_OWNER_MISMATCH");
    }

    @Test void rejectsUnknownRulesInsideRuleSets() {
        ValidationModel base = model(rule());
        ValidationModel invalid = new ValidationModel("1.0", base.id(), base.metamodelId(), base.expressionModelId(), base.actionModelId(),
                List.of(new ValidationRuleSet("set:invalid", "Invalid", "", true, 0, List.of("rule:missing"), Map.of())), base.rules(), List.of(), Map.of());
        ValidationModelCheckReport report = checker.check(invalid, ExpressionTestFixtures.metamodel(),
                ExpressionTestFixtures.expressionModel(ExpressionTestFixtures.positiveAge()), actions());
        assertThat(report.diagnostics()).extracting(ValidationModelDiagnostic::code).contains("UNKNOWN_RULE_IN_SET");
    }

    private ValidationRule rule() {
        return new ValidationRule("rule:positive-age", "Positive age", "POSITIVE_AGE", "", true,
                new ValidationContext(ValidationContextKind.ATTRIBUTE, ExpressionTestFixtures.PERSON, ExpressionTestFixtures.AGE), ValidationSeverity.ERROR,
                ExpressionTestFixtures.positiveAge().id(), "Age must be positive", List.of(ValidationTrigger.ON_ATTRIBUTE_CHANGE, ValidationTrigger.ON_SAVE),
                List.of(new ValidationDependency(ValidationDependencyKind.CLASS, ExpressionTestFixtures.PERSON), new ValidationDependency(ValidationDependencyKind.ATTRIBUTE, ExpressionTestFixtures.AGE)),
                List.of("action:set-age-zero"), ValidationExecutionMode.INCREMENTAL, AuthoritativeRepresentation.CANONICAL, ConversionStatus.FULL, null, Map.of());
    }
    private ActionModel actions() {
        ActionDefinition action = new ActionDefinition("action:set-age-zero", "Set age to zero", "", true, List.of(), "",
                List.of(new SetAttributeOperation("operation:set-age", new ActionSelector(ActionSelectorKind.SELF, "", ""), ExpressionTestFixtures.AGE, ActionValueSource.literal(0))),
                ActionTransactionPolicy.ATOMIC, ActionUndoPolicy.REQUIRED, Map.of());
        return new ActionModel("1.0", "library-actions", ExpressionTestFixtures.METAMODEL_ID, ExpressionTestFixtures.METAMODEL_ID, List.of(action), Map.of());
    }
    private ValidationModel model(ValidationRule... rules) {
        List<String> ids = List.of(rules).stream().map(ValidationRule::id).toList();
        return new ValidationModel("1.0", "library-validations", ExpressionTestFixtures.METAMODEL_ID, ExpressionTestFixtures.METAMODEL_ID,
                "library-actions", List.of(new ValidationRuleSet("set:default", "Default", "", true, 0, ids, Map.of())), List.of(rules), List.of(), Map.of());
    }
}
