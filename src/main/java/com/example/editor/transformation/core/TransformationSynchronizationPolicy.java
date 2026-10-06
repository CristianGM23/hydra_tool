package com.example.editor.transformation.core;

/**
 * Policy persisted with the canonical definition for incremental bidirectional synchronization.
 * Fix38 executes either direction in deterministic batch mode and records the trace evidence that layer needs.
 */
public record TransformationSynchronizationPolicy(
        TransformationConflictPolicy conflictPolicy,
        boolean propagateDeletions,
        boolean requireTraceForUpdates) {

    public TransformationSynchronizationPolicy {
        conflictPolicy = conflictPolicy == null ? TransformationConflictPolicy.FAIL_ON_CONFLICT : conflictPolicy;
    }

    /** Descripción: Devuelve la política conservadora por defecto para sincronización futura. Entradas: no requiere parámetros. Salida: política fail-closed con traza obligatoria para actualizar. */
    /** Descripción: Ejecuta la operación «defaults» correspondiente a la responsabilidad de TransformationSynchronizationPolicy. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public static TransformationSynchronizationPolicy defaults() {
        return new TransformationSynchronizationPolicy(TransformationConflictPolicy.FAIL_ON_CONFLICT, false, true);
    }
}
