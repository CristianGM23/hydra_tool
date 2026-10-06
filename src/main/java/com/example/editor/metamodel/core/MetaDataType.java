package com.example.editor.metamodel.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Describe un tipo de datos neutral. Su clase Java y serialización Ecore se
 * configuran fuera del núcleo canónico.
 */
public record MetaDataType(String id, String name, Map<String, String> annotations) {

    public MetaDataType {
        id = id == null ? "" : id;
        name = name == null ? "" : name;
        annotations = immutableMap(annotations);
    }

    /** Descripción: Ejecuta la operación «immutableMap» correspondiente a la responsabilidad de MetaDataType. Entradas: values: colección de elementos que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static Map<String, String> immutableMap(Map<String, String> values) {
        if(values == null || values.isEmpty()) {
            return Map.of();
        }

        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }
}
