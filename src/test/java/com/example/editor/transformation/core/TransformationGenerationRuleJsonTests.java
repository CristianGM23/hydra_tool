package com.example.editor.transformation.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Regresión del defecto hallado por el E2E real: una regla M2T sin condición se devolvía con {@code "guard": null} y el diseñador React se rompía. */
class TransformationGenerationRuleJsonTests {
    private final JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();

    @Test
    @DisplayName("una regla de generación sin condición no serializa el campo guard (igual que las demás reglas de transformación)")
    void aRuleWithoutConditionOmitsTheGuardField() throws Exception {
        TransformationGenerationRule rule = new TransformationGenerationRule("rule:one", "One", true, "source", "class:Person", null,
                "out/{id}.txt", "hello", GenerationOverwritePolicy.FAIL, Map.of());
        String json = mapper.writeValueAsString(rule);
        JsonNode tree = mapper.readTree(json);
        assertThat(tree.has("guard")).as("guard must be absent, not null: " + json).isFalse();
        assertThat(tree.get("id").asString()).isEqualTo("rule:one");
        assertThat(mapper.readValue(json, TransformationGenerationRule.class)).isEqualTo(rule);
    }
}
