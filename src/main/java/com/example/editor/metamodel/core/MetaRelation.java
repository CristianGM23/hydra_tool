package com.example.editor.metamodel.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Describe una relación neutral entre clasificadores. Las opciones de runtime
 * Ecore se guardan por separado en EcoreRelationConfiguration.
 */
public record MetaRelation(String id, String name, String sourceTypeId, String targetTypeId, MetaRelationKind kind, Multiplicity sourceMultiplicity, Multiplicity targetMultiplicity, String oppositeRelationId, boolean ordered, boolean unique, Map<String, String> annotations) {

    public MetaRelation {
        id = id == null ? "" : id;
        name = name == null ? "" : name;
        sourceTypeId = sourceTypeId == null ? "" : sourceTypeId;
        targetTypeId = targetTypeId == null ? "" : targetTypeId;
        oppositeRelationId = oppositeRelationId == null ? "" : oppositeRelationId;
        annotations = immutableMap(annotations);
    }

    /** Descripción: Ejecuta la operación «immutableMap» correspondiente a la responsabilidad de MetaRelation. Entradas: values: colección de elementos que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static Map<String, String> immutableMap(Map<String, String> values) {
        if(values == null || values.isEmpty()) {
            return Map.of();
        }

        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }
}
