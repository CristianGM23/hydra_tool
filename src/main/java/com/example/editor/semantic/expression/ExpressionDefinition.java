package com.example.editor.semantic.expression;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.example.editor.contract.StableIdentifierPolicy;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Expresión reutilizable con contexto y tipo de retorno opcional. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExpressionDefinition(String id, String name, String description, String contextClassId, ExpressionValueType declaredReturnType, ExpressionNode root, Map<String, String> annotations) {
    public ExpressionDefinition {
        id = StableIdentifierPolicy.requireElementId(id, "id");
        if(name == null || name.isBlank()) throw new IllegalArgumentException("name must not be blank");
        description = description == null ? "" : description;
        contextClassId = contextClassId == null ? "" : contextClassId;
        if(!contextClassId.isBlank()) StableIdentifierPolicy.requireElementId(contextClassId, "contextClassId");
        if(root == null) throw new IllegalArgumentException("root must not be null");
        if(annotations == null || annotations.isEmpty()) annotations = Map.of();
        else annotations = Collections.unmodifiableMap(new LinkedHashMap<>(annotations));
    }
}
