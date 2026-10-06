package com.example.editor.definition;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.example.editor.validation.contract.StableIdentifierPolicy;
import com.fasterxml.jackson.annotation.JsonInclude;

/** Capa visual neutral de una representación. Conserva pertenencia y activación sin ejecutar semántica externa. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record RepresentationLayerDefinition(String id, String name, boolean defaultLayer, boolean activeByDefault,
        boolean userToggleable, List<String> nodeMappingIds, List<String> edgeMappingIds, List<String> toolIds,
        Map<String,String> annotations) {
    public RepresentationLayerDefinition {
        id = StableIdentifierPolicy.requireElementId(id, "representationLayer.id");
        name = name == null || name.isBlank() ? id : name.trim();
        nodeMappingIds = normalizeIds(nodeMappingIds, "nodeMappingIds");
        edgeMappingIds = normalizeIds(edgeMappingIds, "edgeMappingIds");
        toolIds = normalizeIds(toolIds, "toolIds");
        annotations = immutable(annotations);
        if(defaultLayer && !activeByDefault) throw new IllegalArgumentException("The default representation layer must be active by default");
    }

    /** Descripción: Ejecuta la operación «normalizeIds» correspondiente a la responsabilidad de RepresentationLayerDefinition. Entradas: values: colección de elementos que debe procesarse; field: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static List<String> normalizeIds(List<String> values, String field) {
        if(values == null || values.isEmpty()) return List.of();
        return values.stream().map(value -> StableIdentifierPolicy.requireElementId(value, field)).distinct().toList();
    }
    /** Descripción: Ejecuta la operación «immutable» correspondiente a la responsabilidad de RepresentationLayerDefinition. Entradas: values: colección de elementos que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static Map<String,String> immutable(Map<String,String> values) {
        return values == null || values.isEmpty() ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }
}
