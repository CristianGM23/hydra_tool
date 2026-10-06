package com.example.editor.validation.runtime;

import java.util.List;

/** Cambios semánticos utilizados para seleccionar reglas incrementales. */
public record ValidationChangeSet(List<String> elementIds, List<String> classIds, List<String> attributeIds, List<String> relationIds, List<String> operationIds, boolean modelChanged) {
    public ValidationChangeSet {
        elementIds = copy(elementIds);
        classIds = copy(classIds);
        attributeIds = copy(attributeIds);
        relationIds = copy(relationIds);
        operationIds = copy(operationIds);
    }
    /** Descripción: Ejecuta la operación «empty» correspondiente a la responsabilidad de ValidationChangeSet. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public static ValidationChangeSet empty() { return new ValidationChangeSet(List.of(), List.of(), List.of(), List.of(), List.of(), false); }
    /** Descripción: Evalúa la condición «isEmpty» sin modificar el estado externo. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean isEmpty() { return !modelChanged && elementIds.isEmpty() && classIds.isEmpty() && attributeIds.isEmpty() && relationIds.isEmpty() && operationIds.isEmpty(); }
    /** Descripción: Ejecuta la operación «copy» correspondiente a la responsabilidad de ValidationChangeSet. Entradas: values: colección de elementos que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static List<String> copy(List<String> values) { return values == null ? List.of() : values.stream().filter(value -> value != null && !value.isBlank()).distinct().toList(); }
}
