package com.example.editor.validation.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.example.editor.validation.contract.StableIdentifierPolicy;

/** Representa una instancia neutral de una clase del metamodelo canónico. */
public record CanonicalModelElement(String id, String classId, Map<String, Object> attributes, Map<String, String> annotations) {

    public CanonicalModelElement {
        id = StableIdentifierPolicy.requireElementId(id, "id");
        classId = StableIdentifierPolicy.requireElementId(classId, "classId");
        attributes = immutableObjectMap(attributes);
        annotations = immutableStringMap(annotations);
    }

    /** Descripción: Ejecuta la operación «immutableObjectMap» correspondiente a la responsabilidad de CanonicalModelElement. Entradas: values: colección de elementos que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static Map<String, Object> immutableObjectMap(Map<String, Object> values) {
        if(values == null || values.isEmpty()) return Map.of();
        Map<String, Object> normalized = new LinkedHashMap<>();
        values.forEach((key, value) -> normalized.put(StableIdentifierPolicy.requireElementId(key, "attributeId"), value));
        return Collections.unmodifiableMap(normalized);
    }

    /** Descripción: Ejecuta la operación «immutableStringMap» correspondiente a la responsabilidad de CanonicalModelElement. Entradas: values: colección de elementos que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static Map<String, String> immutableStringMap(Map<String, String> values) {
        if(values == null || values.isEmpty()) return Map.of();
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }
}
