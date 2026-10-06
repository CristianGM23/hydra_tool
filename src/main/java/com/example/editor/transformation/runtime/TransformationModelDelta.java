package com.example.editor.transformation.runtime;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Delta neutral y serializable entre dos CanonicalModelDocument. */
public record TransformationModelDelta(
        List<String> addedElementIds,
        List<String> removedElementIds,
        List<String> modifiedElementIds,
        Map<String, List<String>> modifiedAttributeIds,
        List<String> addedRelationIds,
        List<String> removedRelationIds,
        List<String> modifiedRelationIds) {
    public TransformationModelDelta {
        addedElementIds = copy(addedElementIds);
        removedElementIds = copy(removedElementIds);
        modifiedElementIds = copy(modifiedElementIds);
        addedRelationIds = copy(addedRelationIds);
        removedRelationIds = copy(removedRelationIds);
        modifiedRelationIds = copy(modifiedRelationIds);
        if(modifiedAttributeIds == null || modifiedAttributeIds.isEmpty()) modifiedAttributeIds = Map.of();
        else {
            Map<String,List<String>> safe = new LinkedHashMap<>();
            modifiedAttributeIds.forEach((key,value) -> safe.put(key, copy(value)));
            modifiedAttributeIds = Collections.unmodifiableMap(safe);
        }
    }

    /** Descripción: Ejecuta la operación «empty» correspondiente a la responsabilidad de TransformationModelDelta. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public static TransformationModelDelta empty() {
        return new TransformationModelDelta(List.of(), List.of(), List.of(), Map.of(), List.of(), List.of(), List.of());
    }

    /** Descripción: Ejecuta la operación «emptyDelta» correspondiente a la responsabilidad de TransformationModelDelta. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean emptyDelta() {
        return addedElementIds.isEmpty() && removedElementIds.isEmpty() && modifiedElementIds.isEmpty()
                && addedRelationIds.isEmpty() && removedRelationIds.isEmpty() && modifiedRelationIds.isEmpty();
    }

    /** Descripción: Ejecuta la operación «copy» correspondiente a la responsabilidad de TransformationModelDelta. Entradas: values: colección de elementos que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static List<String> copy(List<String> values) { return values == null ? List.of() : List.copyOf(values); }
}
