package com.example.editor.validation.runtime;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.example.editor.metamodel.behavior.OperationQueryRuntime;
import com.example.editor.metamodel.core.OperationBehaviorModel;
import com.example.editor.semantic.expression.BinaryExpressionNode;
import com.example.editor.semantic.expression.BinaryExpressionOperator;
import com.example.editor.semantic.expression.ExpressionDefinition;
import com.example.editor.semantic.expression.ExpressionDependencyAnalyzer;
import com.example.editor.semantic.expression.ExpressionEvaluator;
import com.example.editor.validation.expression.ExpressionModel;
import com.example.editor.semantic.expression.ExpressionTypeChecker;
import com.example.editor.semantic.expression.ExpressionValueKind;
import com.example.editor.semantic.expression.ExpressionValueType;
import com.example.editor.semantic.expression.LiteralExpressionNode;
import com.example.editor.validation.validation.AuthoritativeRepresentation;
import com.example.editor.validation.validation.ConversionStatus;
import com.example.editor.validation.validation.ValidationContext;
import com.example.editor.validation.validation.ValidationContextKind;
import com.example.editor.validation.validation.ValidationExecutionMode;
import com.example.editor.validation.validation.ValidationModelChecker;
import com.example.editor.validation.validation.ValidationRule;
import com.example.editor.validation.validation.ValidationSeverity;
import com.example.editor.validation.validation.ValidationTrigger;
import static org.assertj.core.api.Assertions.assertThat;

class ValidationEngineTests {
    private final ExpressionTypeChecker typeChecker = new ExpressionTypeChecker(new ExpressionDependencyAnalyzer());
    private final ExpressionEvaluator evaluator = new ExpressionEvaluator(typeChecker);
    private final ValidationCompiler compiler = new ValidationCompiler(new ValidationModelChecker(typeChecker), new ValidationPlanCache());
    private final ValidationEngine engine = new ValidationEngine(evaluator, new OperationQueryRuntime(evaluator), Clock.fixed(Instant.parse("2026-07-27T00:00:00Z"), ZoneOffset.UTC));
    private final OperationBehaviorModel behaviors = new OperationBehaviorModel("1.0", ValidationRuntimeTestFixtures.METAMODEL, List.of(), Map.of());

    @Test
    void fullValidationFindsOnlyInvalidPerson() {
        ModelValidationReport report = validate(ValidationRuntimeTestFixtures.request(ValidationRunMode.FULL));
        assertThat(report.diagnostics()).extracting(ModelValidationDiagnostic::targetElementId).containsExactly("person:bob");
        assertThat(report.valid()).isFalse();
    }

    @Test
    void selectionValidationRestrictsCandidates() {
        ValidationExecutionRequest request = new ValidationExecutionRequest(ValidationRunMode.SELECTION, ValidationRuntimeTestFixtures.model(), List.of("person:alice"), "", ValidationChangeSet.empty(), Map.of());
        assertThat(validate(request).diagnostics()).isEmpty();
    }

    @Test
    void classValidationRunsOnlyRequestedClass() {
        ValidationExecutionRequest request = new ValidationExecutionRequest(ValidationRunMode.CLASS, ValidationRuntimeTestFixtures.model(), List.of(), ValidationRuntimeTestFixtures.PERSON, ValidationChangeSet.empty(), Map.of());
        assertThat(validate(request).executedRuleIds()).containsExactly(ValidationRuntimeTestFixtures.RULE);
    }

    @Test
    void onSaveUsesOnlyRulesWithOnSaveTrigger() {
        ModelValidationReport report = validate(ValidationRuntimeTestFixtures.request(ValidationRunMode.ON_SAVE));
        assertThat(report.executedRuleIds()).containsExactly(ValidationRuntimeTestFixtures.RULE);
    }

    @Test
    void manualModeSkipsOrdinaryRule() {
        ModelValidationReport report = validate(ValidationRuntimeTestFixtures.request(ValidationRunMode.MANUAL));
        assertThat(report.executedRuleIds()).isEmpty();
        assertThat(report.skippedRuleIds()).contains(ValidationRuntimeTestFixtures.RULE);
    }

    @Test
    void incrementalModeUsesChangedAttributeAndElement() {
        ValidationChangeSet changes = new ValidationChangeSet(List.of("person:bob"), List.of(), List.of(ValidationRuntimeTestFixtures.AGE), List.of(), List.of(), false);
        ValidationExecutionRequest request = new ValidationExecutionRequest(ValidationRunMode.INCREMENTAL, ValidationRuntimeTestFixtures.model(), List.of(), "", changes, Map.of());
        ModelValidationReport report = validate(request);
        assertThat(report.executedRuleIds()).containsExactly(ValidationRuntimeTestFixtures.RULE);
        assertThat(report.diagnostics()).extracting(ModelValidationDiagnostic::targetElementId).containsExactly("person:bob");
    }

    @Test
    void incrementalModeSkipsUnrelatedRules() {
        ValidationChangeSet changes = new ValidationChangeSet(List.of("person:bob"), List.of(), List.of("attribute:Person:name"), List.of(), List.of(), false);
        ValidationExecutionRequest request = new ValidationExecutionRequest(ValidationRunMode.INCREMENTAL, ValidationRuntimeTestFixtures.model(), List.of(), "", changes, Map.of());
        ModelValidationReport report = validate(request);
        assertThat(report.executedRuleIds()).isEmpty();
        assertThat(report.diagnostics()).isEmpty();
    }

    @Test
    void diagnosticIdentifierIsStableAcrossRuns() {
        String first = validate(ValidationRuntimeTestFixtures.request(ValidationRunMode.FULL)).diagnostics().getFirst().id();
        String second = validate(ValidationRuntimeTestFixtures.request(ValidationRunMode.FULL)).diagnostics().getFirst().id();
        assertThat(first).isEqualTo(second).startsWith("diagnostic-");
    }

    @Test
    void messageTemplateReceivesElementAndClass() {
        ModelValidationDiagnostic diagnostic = validate(ValidationRuntimeTestFixtures.request(ValidationRunMode.FULL)).diagnostics().getFirst();
        assertThat(diagnostic.message()).isEqualTo("person:bob must have a positive age");
        assertThat(diagnostic.targetClassId()).isEqualTo(ValidationRuntimeTestFixtures.PERSON);
    }

    @Test
    void runtimeErrorsAreSeparatedFromViolations() {
        ExpressionDefinition faulty = new ExpressionDefinition("expression:faulty", "Faulty", "", ValidationRuntimeTestFixtures.PERSON,
                ExpressionValueType.scalar(ExpressionValueKind.BOOLEAN),
                new BinaryExpressionNode("node:greater", BinaryExpressionOperator.GREATER_THAN,
                        new BinaryExpressionNode("node:divide", BinaryExpressionOperator.DIVIDE,
                                new LiteralExpressionNode("node:one", ExpressionValueType.scalar(ExpressionValueKind.INTEGER), 1),
                                new LiteralExpressionNode("node:zero", ExpressionValueType.scalar(ExpressionValueKind.INTEGER), 0)),
                        new LiteralExpressionNode("node:limit", ExpressionValueType.scalar(ExpressionValueKind.INTEGER), 0)), Map.of());
        ExpressionModel expressions = new ExpressionModel("1.0", "people-expressions", ValidationRuntimeTestFixtures.METAMODEL, List.of(faulty), Map.of());
        ValidationRule rule = new ValidationRule("rule:faulty", "Faulty", "FAULTY", "", true,
                new ValidationContext(ValidationContextKind.CLASS, ValidationRuntimeTestFixtures.PERSON, ""), ValidationSeverity.ERROR,
                faulty.id(), "Faulty", List.of(ValidationTrigger.FULL_VALIDATION), List.of(), List.of(), ValidationExecutionMode.INCREMENTAL,
                AuthoritativeRepresentation.CANONICAL, ConversionStatus.FULL, null, Map.of());
        ExecutableValidationPlan plan = compiler.compile(ValidationRuntimeTestFixtures.metamodel(), expressions, ValidationRuntimeTestFixtures.actions(),
                ValidationRuntimeTestFixtures.validations(rule), ValidationRuntimeTestFixtures.versions()).plan();
        ModelValidationReport report = engine.validate(plan, ValidationRuntimeTestFixtures.metamodel(), expressions, behaviors, ValidationRuntimeTestFixtures.request(ValidationRunMode.FULL));
        assertThat(report.diagnostics()).allMatch(item -> item.status() == ValidationDiagnosticStatus.RULE_EXECUTION_ERROR);
    }

    @Test
    void brokenCompiledRuleIsReportedWithoutStoppingHealthyRules() {
        ExecutableValidationPlan healthy = plan();
        ExecutableValidationPlan mixed = new ExecutableValidationPlan("1.0", healthy.metamodelId(), healthy.metamodelEtag(), healthy.validationModelId(), healthy.validationModelEtag(),
                healthy.rules(), healthy.id(), healthy.expressionModelId(), healthy.actionModelId(), healthy.sourceFingerprint(), healthy.sourceVersions(), healthy.dependencyIndex(),
                List.of(new ValidationCompilationDiagnostic(ValidationCompilationSeverity.ERROR, "UNKNOWN_EXPRESSION", "rule:broken", "Missing expression")), Map.of());
        ModelValidationReport report = engine.validate(mixed, ValidationRuntimeTestFixtures.metamodel(), ValidationRuntimeTestFixtures.expressions(), behaviors, ValidationRuntimeTestFixtures.request(ValidationRunMode.FULL));
        assertThat(report.diagnostics()).anyMatch(item -> item.status() == ValidationDiagnosticStatus.BROKEN_RULE && item.ruleId().equals("rule:broken"));
        assertThat(report.diagnostics()).anyMatch(item -> item.status() == ValidationDiagnosticStatus.VIOLATION && item.ruleId().equals(ValidationRuntimeTestFixtures.RULE));
    }

    @Test
    void reportContainsDeterministicExecutionMetadata() {
        ModelValidationReport report = validate(ValidationRuntimeTestFixtures.request(ValidationRunMode.FULL));
        assertThat(report.startedAt()).isEqualTo(Instant.parse("2026-07-27T00:00:00Z"));
        assertThat(report.completedAt()).isEqualTo(report.startedAt());
        assertThat(report.durationMillis()).isZero();
        assertThat(report.planId()).isEqualTo("people-validations-plan");
    }

    private ExecutableValidationPlan plan() {
        return compiler.compile(ValidationRuntimeTestFixtures.metamodel(), ValidationRuntimeTestFixtures.expressions(), ValidationRuntimeTestFixtures.actions(),
                ValidationRuntimeTestFixtures.validations(), ValidationRuntimeTestFixtures.versions()).plan();
    }

    private ModelValidationReport validate(ValidationExecutionRequest request) {
        return engine.validate(plan(), ValidationRuntimeTestFixtures.metamodel(), ValidationRuntimeTestFixtures.expressions(), behaviors, request);
    }
}
