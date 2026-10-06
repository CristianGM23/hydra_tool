package com.example.editor.validation.runtime;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.example.editor.metamodel.behavior.OperationQueryRuntime;
import com.example.editor.metamodel.core.OperationBehaviorModel;
import com.example.editor.semantic.expression.ExpressionDependencyAnalyzer;
import com.example.editor.semantic.expression.ExpressionEvaluator;
import com.example.editor.semantic.expression.ExpressionTypeChecker;
import com.example.editor.validation.validation.ValidationModelChecker;
import static org.assertj.core.api.Assertions.assertThat;

/** Regresiones del filtrado incremental: los cambios específicos nunca deben degradarse a clase. */
class ValidationEngineIncrementalSelectionRegressionTests {
    private final ExpressionTypeChecker typeChecker = new ExpressionTypeChecker(new ExpressionDependencyAnalyzer());
    private final ExpressionEvaluator evaluator = new ExpressionEvaluator(typeChecker);
    private final ValidationCompiler compiler = new ValidationCompiler(new ValidationModelChecker(typeChecker), new ValidationPlanCache());
    private final ValidationEngine engine = new ValidationEngine(evaluator, new OperationQueryRuntime(evaluator),
            Clock.fixed(Instant.parse("2026-07-27T00:00:00Z"), ZoneOffset.UTC));
    private final OperationBehaviorModel behaviors = new OperationBehaviorModel("1.0",
            ValidationRuntimeTestFixtures.METAMODEL, List.of(), Map.of());

    @Test
    void matchingAttributeAndInvalidElementExecutesRule() {
        ModelValidationReport report = validate(changes(List.of("person:bob"), List.of(),
                List.of(ValidationRuntimeTestFixtures.AGE), List.of(), List.of()));
        assertThat(report.executedRuleIds()).containsExactly(ValidationRuntimeTestFixtures.RULE);
        assertThat(report.diagnostics()).extracting(ModelValidationDiagnostic::targetElementId)
                .containsExactly("person:bob");
    }

    @Test
    void matchingAttributeAndValidElementExecutesWithoutViolation() {
        ModelValidationReport report = validate(changes(List.of("person:alice"), List.of(),
                List.of(ValidationRuntimeTestFixtures.AGE), List.of(), List.of()));
        assertThat(report.executedRuleIds()).containsExactly(ValidationRuntimeTestFixtures.RULE);
        assertThat(report.diagnostics()).isEmpty();
    }

    @Test
    void matchingClassExecutesRule() {
        ModelValidationReport report = validate(changes(List.of(), List.of(ValidationRuntimeTestFixtures.PERSON),
                List.of(), List.of(), List.of()));
        assertThat(report.executedRuleIds()).containsExactly(ValidationRuntimeTestFixtures.RULE);
    }

    @Test
    void onlyElementIdentifierFallsBackToItsClass() {
        ModelValidationReport report = validate(changes(List.of("person:bob"), List.of(),
                List.of(), List.of(), List.of()));
        assertThat(report.executedRuleIds()).containsExactly(ValidationRuntimeTestFixtures.RULE);
    }

    @Test
    void unrelatedAttributeWithElementDoesNotFallBackToClass() {
        ModelValidationReport report = validate(changes(List.of("person:bob"), List.of(),
                List.of("attribute:Person:name"), List.of(), List.of()));
        assertThat(report.executedRuleIds()).isEmpty();
        assertThat(report.diagnostics()).isEmpty();
    }

    @Test
    void unrelatedAttributeWithoutElementSkipsRule() {
        ModelValidationReport report = validate(changes(List.of(), List.of(),
                List.of("attribute:Person:name"), List.of(), List.of()));
        assertThat(report.executedRuleIds()).isEmpty();
    }

    @Test
    void unrelatedClassWithElementDoesNotFallBackToElementClass() {
        ModelValidationReport report = validate(changes(List.of("person:bob"), List.of("class:Company"),
                List.of(), List.of(), List.of()));
        assertThat(report.executedRuleIds()).isEmpty();
    }

    @Test
    void unrelatedRelationWithElementDoesNotFallBackToClass() {
        ModelValidationReport report = validate(changes(List.of("person:bob"), List.of(),
                List.of(), List.of("relation:Person:employer"), List.of()));
        assertThat(report.executedRuleIds()).isEmpty();
    }

    @Test
    void unrelatedOperationWithElementDoesNotFallBackToClass() {
        ModelValidationReport report = validate(changes(List.of("person:bob"), List.of(),
                List.of(), List.of(), List.of("operation:Person:calculate")));
        assertThat(report.executedRuleIds()).isEmpty();
    }

    @Test
    void unknownElementWithoutSpecificMetadataDoesNotSelectRule() {
        ModelValidationReport report = validate(changes(List.of("person:missing"), List.of(),
                List.of(), List.of(), List.of()));
        assertThat(report.executedRuleIds()).isEmpty();
    }

    private ValidationChangeSet changes(List<String> elements, List<String> classes, List<String> attributes,
            List<String> relations, List<String> operations) {
        return new ValidationChangeSet(elements, classes, attributes, relations, operations, false);
    }

    private ModelValidationReport validate(ValidationChangeSet changeSet) {
        ValidationExecutionRequest request = new ValidationExecutionRequest(ValidationRunMode.INCREMENTAL,
                ValidationRuntimeTestFixtures.model(), List.of(), "", changeSet, Map.of());
        return engine.validate(plan(), ValidationRuntimeTestFixtures.metamodel(),
                ValidationRuntimeTestFixtures.expressions(), behaviors, request);
    }

    private ExecutableValidationPlan plan() {
        return compiler.compile(ValidationRuntimeTestFixtures.metamodel(),
                ValidationRuntimeTestFixtures.expressions(), ValidationRuntimeTestFixtures.actions(),
                ValidationRuntimeTestFixtures.validations(), ValidationRuntimeTestFixtures.versions()).plan();
    }
}
