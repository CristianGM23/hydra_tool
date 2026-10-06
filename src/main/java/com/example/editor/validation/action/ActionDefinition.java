package com.example.editor.validation.action;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.example.editor.validation.contract.StableIdentifierPolicy;
import com.fasterxml.jackson.annotation.JsonInclude;

/** Acción reutilizable por quick fixes, paletas y formularios. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ActionDefinition(String id, String name, String description, boolean enabled, @JsonInclude(JsonInclude.Include.ALWAYS) List<ActionParameter> parameters, String preconditionExpressionId, @JsonInclude(JsonInclude.Include.ALWAYS) List<ActionOperation> operations, ActionTransactionPolicy transactionPolicy, ActionUndoPolicy undoPolicy, Map<String, String> annotations) {
    public ActionDefinition {
        id = StableIdentifierPolicy.requireElementId(id, "id");
        if(name == null || name.isBlank()) throw new IllegalArgumentException("name must not be blank");
        description = description == null ? "" : description;
        parameters = parameters == null ? List.of() : List.copyOf(parameters);
        preconditionExpressionId = preconditionExpressionId == null ? "" : preconditionExpressionId;
        if(!preconditionExpressionId.isBlank()) StableIdentifierPolicy.requireElementId(preconditionExpressionId, "preconditionExpressionId");
        operations = operations == null ? List.of() : List.copyOf(operations);
        annotations = annotations == null || annotations.isEmpty() ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(annotations));
        boolean reviewOnly = "true".equalsIgnoreCase(annotations.getOrDefault("hydra.import.reviewOnly", "false"));
        if(operations.isEmpty() && (!reviewOnly || enabled)) throw new IllegalArgumentException("operations must not be empty unless the action is a disabled imported review-only quick fix");
        if(transactionPolicy == null) throw new IllegalArgumentException("transactionPolicy must not be null");
        if(undoPolicy == null) throw new IllegalArgumentException("undoPolicy must not be null");
    }
}
