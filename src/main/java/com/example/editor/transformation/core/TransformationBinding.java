package com.example.editor.transformation.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.example.editor.semantic.expression.ExpressionDefinition;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TransformationBinding(
        String id,
        String targetAlias,
        String targetFeatureId,
        TransformationBindingKind kind,
        String sourceFeatureId,
        Object literalValue,
        ExpressionDefinition expression,
        TransformationValuePolicy valuePolicy,
        Map<String, String> annotations) {

    public TransformationBinding {
        id = text(id);
        targetAlias = text(targetAlias);
        targetFeatureId = text(targetFeatureId);
        kind = kind == null ? TransformationBindingKind.DIRECT : kind;
        sourceFeatureId = text(sourceFeatureId);
        valuePolicy = valuePolicy == null ? TransformationValuePolicy.defaults() : valuePolicy;
        annotations = immutable(annotations);
    }

    /** Descripción: Ejecuta la operación «text» correspondiente a la responsabilidad de TransformationBinding. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String text(String value) {
        return value == null ? "" : value.trim();
    }

    /** Descripción: Ejecuta la operación «immutable» correspondiente a la responsabilidad de TransformationBinding. Entradas: values: colección de elementos que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static Map<String, String> immutable(Map<String, String> values) {
        if(values == null || values.isEmpty()) return Map.of();
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }
}
