package com.example.editor.metamodel.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Describe un atributo neutral. Las opciones específicas de Ecore se guardan
 * en EcoreAttributeConfiguration y no forman parte del significado canónico.
 */
public record MetaAttribute(String id, String name, MetaTypeReference type, Multiplicity multiplicity, boolean ordered, boolean unique, String defaultValue, Map<String, String> annotations) {

    public MetaAttribute {
        id = id == null ? "" : id;
        name = name == null ? "" : name;
        defaultValue = defaultValue == null ? "" : defaultValue;
        annotations = immutableMap(annotations);
    }

    /** Descripción: Ejecuta la operación «immutableMap» correspondiente a la responsabilidad de MetaAttribute. Entradas: values: colección de elementos que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static Map<String, String> immutableMap(Map<String, String> values) {
        if(values == null || values.isEmpty()) {
            return Map.of();
        }

        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }
}
