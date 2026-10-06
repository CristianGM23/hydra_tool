package com.example.editor.transformation.runtime;

import java.util.List;

/** Conflicto incremental localizado sobre una correspondencia estable. */
public record TransformationSynchronizationConflict(
        String correspondenceId,
        String sourceElementId,
        String targetElementId,
        List<String> sourceFeatures,
        List<String> targetFeatures,
        String reason) {
    public TransformationSynchronizationConflict {
        correspondenceId = text(correspondenceId);
        sourceElementId = text(sourceElementId);
        targetElementId = text(targetElementId);
        sourceFeatures = sourceFeatures == null ? List.of() : List.copyOf(sourceFeatures);
        targetFeatures = targetFeatures == null ? List.of() : List.copyOf(targetFeatures);
        reason = text(reason);
    }
    /** Descripción: Ejecuta la operación «text» correspondiente a la responsabilidad de TransformationSynchronizationConflict. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String text(String value) { return value == null ? "" : value; }
}
