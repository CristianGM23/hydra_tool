package com.example.editor.definition;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import com.example.editor.validation.contract.StableIdentifierPolicy;
import com.fasterxml.jackson.annotation.JsonInclude;

/** Regla visual declarativa evaluada contra el elemento representado. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ConditionalStyleRule(String id, String name, boolean enabled, int priority,
        String conditionExpressionId, Map<String,String> styles) {
    public ConditionalStyleRule {
        id = StableIdentifierPolicy.requireElementId(id, "conditionalStyle.id");
        name = name == null || name.isBlank() ? id : name.trim();
        if(priority < -100000 || priority > 100000) throw new IllegalArgumentException("Conditional style priority is outside the supported range");
        conditionExpressionId = StableIdentifierPolicy.requireElementId(conditionExpressionId, "conditionExpressionId");
        styles = styles == null || styles.isEmpty() ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(styles));
    }
}
