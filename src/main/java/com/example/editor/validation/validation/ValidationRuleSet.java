package com.example.editor.validation.validation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.example.editor.validation.contract.StableIdentifierPolicy;
import com.fasterxml.jackson.annotation.JsonInclude;

/** Agrupa y ordena reglas sin duplicar su definición. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ValidationRuleSet(String id, String name, String description, boolean enabled, int order, @JsonInclude(JsonInclude.Include.ALWAYS) List<String> ruleIds, Map<String, String> annotations) {
    public ValidationRuleSet {
        id = StableIdentifierPolicy.requireElementId(id, "id");
        if(name == null || name.isBlank()) throw new IllegalArgumentException("name must not be blank");
        description = description == null ? "" : description;
        if(order < 0) throw new IllegalArgumentException("order must not be negative");
        ruleIds = ruleIds == null ? List.of() : ruleIds.stream().map(value -> StableIdentifierPolicy.requireElementId(value, "ruleId")).toList();
        annotations = annotations == null || annotations.isEmpty() ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(annotations));
    }
}
