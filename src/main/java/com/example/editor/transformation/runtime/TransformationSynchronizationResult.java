package com.example.editor.transformation.runtime;

import java.util.List;

import com.example.editor.transformation.core.TransformationExecutionDirection;
import com.example.editor.validation.model.CanonicalModelDocument;

/** Resultado de reconciliación batch o incremental, con deltas y conflictos explícitos. */
public record TransformationSynchronizationResult(
        CanonicalModelDocument sourceModel,
        CanonicalModelDocument targetModel,
        TransformationExecutionDirection appliedDirection,
        String conflictResolution,
        TransformationTrace trace,
        TransformationSynchronizationMode mode,
        List<TransformationExecutionDirection> appliedDirections,
        TransformationModelDelta sourceDelta,
        TransformationModelDelta targetDelta,
        List<TransformationSynchronizationConflict> conflicts,
        boolean blocked,
        boolean noOp) {
    public TransformationSynchronizationResult {
        appliedDirection = appliedDirection == null ? TransformationExecutionDirection.FORWARD : appliedDirection;
        conflictResolution = conflictResolution == null ? "" : conflictResolution;
        mode = mode == null ? TransformationSynchronizationMode.BATCH : mode;
        appliedDirections = appliedDirections == null ? List.of(appliedDirection) : List.copyOf(appliedDirections);
        sourceDelta = sourceDelta == null ? TransformationModelDelta.empty() : sourceDelta;
        targetDelta = targetDelta == null ? TransformationModelDelta.empty() : targetDelta;
        conflicts = conflicts == null ? List.of() : List.copyOf(conflicts);
    }

    /** Constructor compatible con la respuesta batch introducida en Fix38. */
    /** Descripción: Inicializa una instancia de TransformationSynchronizationResult con sus dependencias y datos de entrada. Entradas: sourceModel: dato de entrada necesario para la operación; targetModel: dato de entrada necesario para la operación; appliedDirection: dato de entrada necesario para la operación; conflictResolution: dato de entrada necesario para la operación; trace: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public TransformationSynchronizationResult(CanonicalModelDocument sourceModel, CanonicalModelDocument targetModel,
            TransformationExecutionDirection appliedDirection, String conflictResolution, TransformationTrace trace) {
        this(sourceModel, targetModel, appliedDirection, conflictResolution, trace, TransformationSynchronizationMode.BATCH,
                List.of(appliedDirection == null ? TransformationExecutionDirection.FORWARD : appliedDirection),
                TransformationModelDelta.empty(), TransformationModelDelta.empty(), List.of(), false, false);
    }
}
