package com.example.editor.validation.runtime;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Índices inmutables para seleccionar reglas sin recorrer todo el plan. */
public record ValidationDependencyIndex(Map<String, List<String>> classRules, Map<String, List<String>> attributeRules,
        Map<String, List<String>> relationRules, Map<String, List<String>> operationRules, Map<String, List<String>> triggerRules,
        List<String> modelRules) {
    public ValidationDependencyIndex {
        classRules = immutable(classRules);
        attributeRules = immutable(attributeRules);
        relationRules = immutable(relationRules);
        operationRules = immutable(operationRules);
        triggerRules = immutable(triggerRules);
        modelRules = modelRules == null ? List.of() : List.copyOf(modelRules);
    }
    /** Descripción: Ejecuta la operación «immutable» correspondiente a la responsabilidad de ValidationDependencyIndex. Entradas: source: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static Map<String, List<String>> immutable(Map<String, List<String>> source) {
        if(source == null || source.isEmpty()) return Map.of();
        Map<String, List<String>> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(key, value == null ? List.of() : List.copyOf(value)));
        return Collections.unmodifiableMap(result);
    }
}
