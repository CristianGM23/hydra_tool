package com.example.editor.validation.runtime;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.example.editor.semantic.expression.ExpressionDependencyAnalyzer;
import com.example.editor.validation.expression.ExpressionModel;
import com.example.editor.semantic.expression.ExpressionTypeChecker;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValidationCompilerTests {
    private final ValidationPlanCache cache = new ValidationPlanCache();
    private final ValidationCompiler compiler = new ValidationCompiler(new ValidationModelChecker(new ExpressionTypeChecker(new ExpressionDependencyAnalyzer())), cache);

    @Test
    void compilesEnabledCanonicalRules() {
        ValidationCompilationResult result = compile(ValidationRuntimeTestFixtures.validations());
        assertThat(result.plan().rules()).extracting(CompiledValidationRule::ruleId).containsExactly(ValidationRuntimeTestFixtures.RULE);
        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    void buildsAttributeAndTriggerIndexes() {
        ExecutableValidationPlan plan = compile(ValidationRuntimeTestFixtures.validations()).plan();
        assertThat(plan.dependencyIndex().attributeRules().get(ValidationRuntimeTestFixtures.AGE)).contains(ValidationRuntimeTestFixtures.RULE);
        assertThat(plan.dependencyIndex().triggerRules().get("ON_SAVE")).contains(ValidationRuntimeTestFixtures.RULE);
    }

    @Test
    void reusesPlanWhenEverySourceVersionMatches() {
        assertThat(compile(ValidationRuntimeTestFixtures.validations()).cacheHit()).isFalse();
        assertThat(compile(ValidationRuntimeTestFixtures.validations()).cacheHit()).isTrue();
        assertThat(cache.size()).isEqualTo(1);
    }

    @Test
    void createsAnotherPlanWhenSourceVersionChanges() {
        compile(ValidationRuntimeTestFixtures.validations());
        ValidationCompilationResult changed = compiler.compile(ValidationRuntimeTestFixtures.metamodel(), ValidationRuntimeTestFixtures.expressions(), ValidationRuntimeTestFixtures.actions(),
                ValidationRuntimeTestFixtures.validations(), new ValidationPlanSourceVersions("m2", "e1", "a1", "v1", "o1"));
        assertThat(changed.cacheHit()).isFalse();
        assertThat(cache.size()).isEqualTo(2);
    }

    @Test
    void sourceContentAlsoInvalidatesCacheWhenCallerHasNoNewEtagYet() {
        compile(ValidationRuntimeTestFixtures.validations());
        ValidationRule disabled = ValidationRuntimeTestFixtures.rule("rule:disabled", false, ValidationExecutionMode.INCREMENTAL,
                List.of(ValidationTrigger.ON_SAVE));
        ValidationCompilationResult changed = compile(ValidationRuntimeTestFixtures.validations(disabled));
        assertThat(changed.cacheHit()).isFalse();
        assertThat(cache.size()).isEqualTo(2);
    }

    @Test
    void omitsDisabledRulesWithoutMakingPlanInvalid() {
        ValidationRule disabled = ValidationRuntimeTestFixtures.rule("rule:disabled", false, ValidationExecutionMode.INCREMENTAL, List.of(ValidationTrigger.ON_SAVE));
        ValidationCompilationResult result = compile(ValidationRuntimeTestFixtures.validations(disabled));
        assertThat(result.plan().rules()).isEmpty();
        assertThat(result.diagnostics()).anyMatch(item -> item.code().equals("RULE_DISABLED") && item.severity() == ValidationCompilationSeverity.INFO);
    }

    @Test
    void preservesExternalRulesAsCompilationWarnings() {
        ValidationRule external = new ValidationRule("rule:external", "External", "EXTERNAL_RULE", "", true,
                new ValidationContext(ValidationContextKind.MODEL, "", ""), ValidationSeverity.WARNING, ValidationRuntimeTestFixtures.EXPRESSION,
                "External", List.of(), List.of(), List.of(), ValidationExecutionMode.EXTERNAL,
                AuthoritativeRepresentation.OCL, ConversionStatus.EXTERNAL, null, Map.of());
        ValidationCompilationResult result = compile(ValidationRuntimeTestFixtures.validations(external));
        assertThat(result.plan().rules()).isEmpty();
        assertThat(result.diagnostics()).anyMatch(item -> item.code().equals("RULE_NOT_EXECUTABLE_IN_CANONICAL_RUNTIME"));
    }

    @Test
    void excludesBrokenRuleAndPublishesError() {
        ValidationRule broken = new ValidationRule("rule:broken", "Broken", "BROKEN", "", true,
                new ValidationContext(ValidationContextKind.CLASS, ValidationRuntimeTestFixtures.PERSON, ""), ValidationSeverity.ERROR,
                "expression:missing", "Broken", List.of(ValidationTrigger.ON_SAVE), List.of(), List.of(), ValidationExecutionMode.INCREMENTAL,
                AuthoritativeRepresentation.CANONICAL, ConversionStatus.FULL, null, Map.of());
        ValidationCompilationResult result = compile(ValidationRuntimeTestFixtures.validations(broken));
        assertThat(result.plan().rules()).isEmpty();
        assertThat(result.hasErrors()).isTrue();
        assertThat(result.diagnostics()).anyMatch(item -> item.ruleId().equals("rule:broken") && item.severity() == ValidationCompilationSeverity.ERROR);
    }

    @Test
    void rejectsSourcesFromDifferentMetamodels() {
        ExpressionModel wrong = new ExpressionModel("1.0", "wrong-expressions", "wrong", ValidationRuntimeTestFixtures.expressions().expressions(), Map.of());
        assertThatThrownBy(() -> compiler.compile(ValidationRuntimeTestFixtures.metamodel(), wrong, ValidationRuntimeTestFixtures.actions(),
                ValidationRuntimeTestFixtures.validations(), ValidationRuntimeTestFixtures.versions())).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("same metamodel");
    }

    @Test
    void planKeepsSourceEtagsAndFingerprint() {
        ExecutableValidationPlan plan = compile(ValidationRuntimeTestFixtures.validations()).plan();
        assertThat(plan.sourceVersions()).isEqualTo(ValidationRuntimeTestFixtures.versions());
        assertThat(plan.sourceFingerprint()).matches("[0-9a-f]{64}");
    }

    @Test
    void invalidationRemovesOnlyPlansOfRequestedMetamodel() {
        compile(ValidationRuntimeTestFixtures.validations());
        cache.invalidateMetamodel(ValidationRuntimeTestFixtures.METAMODEL);
        assertThat(cache.size()).isZero();
    }

    @Test
    void boundedCacheEvictsTheOldestPlan() {
        ValidationPlanCache bounded = new ValidationPlanCache(1);
        ValidationCompiler boundedCompiler = new ValidationCompiler(
                new ValidationModelChecker(new ExpressionTypeChecker(new ExpressionDependencyAnalyzer())), bounded);
        boundedCompiler.compile(ValidationRuntimeTestFixtures.metamodel(), ValidationRuntimeTestFixtures.expressions(),
                ValidationRuntimeTestFixtures.actions(), ValidationRuntimeTestFixtures.validations(), ValidationRuntimeTestFixtures.versions());
        boundedCompiler.compile(ValidationRuntimeTestFixtures.metamodel(), ValidationRuntimeTestFixtures.expressions(),
                ValidationRuntimeTestFixtures.actions(), ValidationRuntimeTestFixtures.validations(),
                new ValidationPlanSourceVersions("m2", "e2", "a2", "v2", "o2"));
        assertThat(bounded.size()).isEqualTo(1);
        assertThat(bounded.maxEntries()).isEqualTo(1);
    }

    private ValidationCompilationResult compile(com.example.editor.validation.validation.ValidationModel model) {
        return compiler.compile(ValidationRuntimeTestFixtures.metamodel(), ValidationRuntimeTestFixtures.expressions(), ValidationRuntimeTestFixtures.actions(), model, ValidationRuntimeTestFixtures.versions());
    }
}
