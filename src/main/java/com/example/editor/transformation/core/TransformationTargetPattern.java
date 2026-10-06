package com.example.editor.transformation.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record TransformationTargetPattern(
        String id,
        String endpointId,
        String typeId,
        String alias,
        Map<String, String> annotations) {

    public TransformationTargetPattern {
        id = text(id);
        endpointId = text(endpointId);
        typeId = text(typeId);
        alias = text(alias);
        annotations = immutable(annotations);
    }

    /** Descripción: Ejecuta la operación «text» correspondiente a la responsabilidad de TransformationTargetPattern. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String text(String value) {
        return value == null ? "" : value.trim();
    }

    /** Descripción: Ejecuta la operación «immutable» correspondiente a la responsabilidad de TransformationTargetPattern. Entradas: values: colección de elementos que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static Map<String, String> immutable(Map<String, String> values) {
        if(values == null || values.isEmpty()) return Map.of();
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }
}
