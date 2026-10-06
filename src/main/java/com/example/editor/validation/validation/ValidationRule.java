package com.example.editor.validation.validation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.example.editor.validation.contract.StableIdentifierPolicy;
import com.fasterxml.jackson.annotation.JsonInclude;

/** Regla canónica editable sin depender del lenguaje del que proceda. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ValidationRule(String id, String name, String code, String description, boolean enabled, ValidationContext context, ValidationSeverity severity, String conditionExpressionId, String messageTemplate, @JsonInclude(JsonInclude.Include.ALWAYS) List<ValidationTrigger> triggers, @JsonInclude(JsonInclude.Include.ALWAYS) List<ValidationDependency> dependencies, @JsonInclude(JsonInclude.Include.ALWAYS) List<String> quickFixIds, ValidationExecutionMode executionMode, AuthoritativeRepresentation authoritativeRepresentation, ConversionStatus conversionStatus, ValidationProvenance provenance, Map<String, String> annotations) {
    public ValidationRule {
        id = StableIdentifierPolicy.requireElementId(id, "id");
        if(name == null || name.isBlank()) throw new IllegalArgumentException("name must not be blank");
        code = StableIdentifierPolicy.requireElementId(code, "code");
        description = description == null ? "" : description;
        if(context == null) throw new IllegalArgumentException("context must not be null");
        if(severity == null) throw new IllegalArgumentException("severity must not be null");
        conditionExpressionId = StableIdentifierPolicy.requireElementId(conditionExpressionId, "conditionExpressionId");
        if(messageTemplate == null || messageTemplate.isBlank()) throw new IllegalArgumentException("messageTemplate must not be blank");
        triggers = triggers == null ? List.of() : List.copyOf(triggers);
        dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
        quickFixIds = quickFixIds == null ? List.of() : quickFixIds.stream().map(value -> StableIdentifierPolicy.requireElementId(value, "quickFixId")).toList();
        if(executionMode == null) throw new IllegalArgumentException("executionMode must not be null");
        if(authoritativeRepresentation == null) throw new IllegalArgumentException("authoritativeRepresentation must not be null");
        if(conversionStatus == null) throw new IllegalArgumentException("conversionStatus must not be null");
        annotations = annotations == null || annotations.isEmpty() ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(annotations));
    }
}
