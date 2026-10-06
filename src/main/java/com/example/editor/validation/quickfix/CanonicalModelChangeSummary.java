package com.example.editor.validation.quickfix;

import java.util.List;
import com.example.editor.validation.runtime.ValidationChangeSet;

/** Resumen estable de los cambios semánticos calculados entre dos documentos. */
public record CanonicalModelChangeSummary(List<String> createdElementIds, List<String> deletedElementIds,
        List<String> modifiedElementIds, List<String> attributeIds, List<String> relationIds, List<String> classIds,
        ValidationChangeSet validationChangeSet) {
    public CanonicalModelChangeSummary {
        createdElementIds = copy(createdElementIds);
        deletedElementIds = copy(deletedElementIds);
        modifiedElementIds = copy(modifiedElementIds);
        attributeIds = copy(attributeIds);
        relationIds = copy(relationIds);
        classIds = copy(classIds);
        if(validationChangeSet == null) validationChangeSet = ValidationChangeSet.empty();
    }
    /** Descripción: Ejecuta la operación «changed» correspondiente a la responsabilidad de CanonicalModelChangeSummary. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean changed() { return !validationChangeSet.isEmpty(); }
    /** Descripción: Ejecuta la operación «copy» correspondiente a la responsabilidad de CanonicalModelChangeSummary. Entradas: values: colección de elementos que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static List<String> copy(List<String> values) { return values == null ? List.of() : List.copyOf(values); }
}
