package com.example.editor.definition;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.example.editor.validation.contract.StableIdentifierPolicy;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Describe una arista cuya identidad semántica es un objeto del modelo y no una referencia directa.
 * Es el patrón usado por Sirius/GMF cuando una instancia (por ejemplo Connection) mantiene referencias
 * independientes hacia sus extremos source y target.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record SemanticEdgeDescriptor(String edgeClassId, String sourceRelationId, String targetRelationId,
        String containmentRelationId, Map<String, Object> defaultValues, Map<String, String> annotations) {
    public SemanticEdgeDescriptor {
        edgeClassId = StableIdentifierPolicy.requireElementId(edgeClassId, "semanticEdge.edgeClassId");
        sourceRelationId = StableIdentifierPolicy.requireElementId(sourceRelationId, "semanticEdge.sourceRelationId");
        targetRelationId = StableIdentifierPolicy.requireElementId(targetRelationId, "semanticEdge.targetRelationId");
        containmentRelationId = optionalId(containmentRelationId, "semanticEdge.containmentRelationId");
        defaultValues = immutableObjects(defaultValues);
        annotations = immutableStrings(annotations);
    }

    /** Descripción: Inicializa una instancia de SemanticEdgeDescriptor con sus dependencias y datos de entrada. Entradas: edgeClassId: identificador que delimita el recurso objetivo; sourceRelationId: identificador que delimita el recurso objetivo; targetRelationId: identificador que delimita el recurso objetivo. Salida: deja la instancia inicializada y lista para su uso. */ public SemanticEdgeDescriptor(String edgeClassId, String sourceRelationId, String targetRelationId) {
        this(edgeClassId, sourceRelationId, targetRelationId, "", Map.of(), Map.of());
    }

    /** Descripción: Ejecuta la operación «withContainmentAndDefaults» correspondiente a la responsabilidad de SemanticEdgeDescriptor. Entradas: containmentRelationId: identificador que delimita el recurso objetivo; defaultValues: dato de entrada necesario para la operación; additionalAnnotations: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public SemanticEdgeDescriptor withContainmentAndDefaults(String containmentRelationId, Map<String,Object> defaultValues,
            Map<String,String> additionalAnnotations) {
        Map<String,String> merged = new LinkedHashMap<>(annotations);
        if(additionalAnnotations != null) merged.putAll(additionalAnnotations);
        return new SemanticEdgeDescriptor(edgeClassId, sourceRelationId, targetRelationId, containmentRelationId,
                defaultValues, merged);
    }

    /** Descripción: Ejecuta la operación «optionalId» correspondiente a la responsabilidad de SemanticEdgeDescriptor. Entradas: value: valor de entrada que debe procesarse; field: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String optionalId(String value, String field) {
        if(value == null || value.isBlank()) return "";
        return StableIdentifierPolicy.requireElementId(value, field);
    }

    /** Descripción: Ejecuta la operación «immutableObjects» correspondiente a la responsabilidad de SemanticEdgeDescriptor. Entradas: values: colección de elementos que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static Map<String,Object> immutableObjects(Map<String,Object> values) {
        if(values == null || values.isEmpty()) return Map.of();
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    /** Descripción: Ejecuta la operación «immutableStrings» correspondiente a la responsabilidad de SemanticEdgeDescriptor. Entradas: values: colección de elementos que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static Map<String,String> immutableStrings(Map<String,String> values) {
        if(values == null || values.isEmpty()) return Map.of();
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }
}
