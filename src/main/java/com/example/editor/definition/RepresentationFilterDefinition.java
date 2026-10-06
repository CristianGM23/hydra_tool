package com.example.editor.definition;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.example.editor.validation.contract.StableIdentifierPolicy;
import com.fasterxml.jackson.annotation.JsonInclude;

/** Filtro neutral de una representación. La condición externa se preserva como texto y nunca se evalúa por sí sola. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record RepresentationFilterDefinition(String id, String name, boolean activeByDefault, List<String> mappingIds,
        String conditionExpressionId, String externalCondition, Map<String,String> annotations) {
    public RepresentationFilterDefinition {
        id = StableIdentifierPolicy.requireElementId(id, "representationFilter.id");
        name = name == null || name.isBlank() ? id : name.trim();
        mappingIds = normalizeIds(mappingIds, "mappingIds");
        conditionExpressionId = normalizeOptionalId(conditionExpressionId, "conditionExpressionId");
        externalCondition = externalCondition == null ? "" : externalCondition.trim();
        annotations = immutable(annotations);
    }

    /** Indica si HYDRA dispone de una expresión canónica que pueda evaluar sin ejecutar el lenguaje externo. */
    /** Descripción: Ejecuta la operación «executable» correspondiente a la responsabilidad de RepresentationFilterDefinition. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean executable() { return !conditionExpressionId.isBlank(); }

    /** Descripción: Ejecuta la operación «normalizeOptionalId» correspondiente a la responsabilidad de RepresentationFilterDefinition. Entradas: value: valor de entrada que debe procesarse; field: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String normalizeOptionalId(String value, String field) {
        if(value == null || value.isBlank()) return "";
        return StableIdentifierPolicy.requireElementId(value, field);
    }
    /** Descripción: Ejecuta la operación «normalizeIds» correspondiente a la responsabilidad de RepresentationFilterDefinition. Entradas: values: colección de elementos que debe procesarse; field: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static List<String> normalizeIds(List<String> values, String field) {
        if(values == null || values.isEmpty()) return List.of();
        return values.stream().map(value -> StableIdentifierPolicy.requireElementId(value, field)).distinct().toList();
    }
    /** Descripción: Ejecuta la operación «immutable» correspondiente a la responsabilidad de RepresentationFilterDefinition. Entradas: values: colección de elementos que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static Map<String,String> immutable(Map<String,String> values) {
        return values == null || values.isEmpty() ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }
}
