package com.example.editor.semantic.expression;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

class ExpressionEvaluationContextTests {

    @Test
    void preservesExplicitNullVariablesForNullAwareExpressions() {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("actor", null);
        variables.put("payload.score", 12);

        ExpressionEvaluationContext context = new ExpressionEvaluationContext(
                ExpressionTestFixtures.metamodel(), ExpressionTestFixtures.model(), "", variables);

        assertThat(context.variables()).containsKey("actor");
        assertThat(context.variable("actor")).isNull();
        assertThat(context.variable("payload.score")).isEqualTo(12);
    }

    @Test
    void defensivelyCopiesVariablesWhileKeepingNullValues() {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("optional", null);
        ExpressionEvaluationContext context = new ExpressionEvaluationContext(
                ExpressionTestFixtures.metamodel(), ExpressionTestFixtures.model(), "", variables);

        variables.put("optional", "changed");
        variables.put("late", 1);

        assertThat(context.variable("optional")).isNull();
        assertThat(context.variables()).doesNotContainKey("late");
        assertThatThrownBy(() -> context.variables().put("x", 1)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void withCurrentAcceptsNullAsARealExpressionValue() {
        ExpressionEvaluationContext context = ExpressionTestFixtures.context("").withCurrent(null);

        assertThat(context.variables()).containsKey("_current");
        assertThat(context.current()).isNull();
    }
}
