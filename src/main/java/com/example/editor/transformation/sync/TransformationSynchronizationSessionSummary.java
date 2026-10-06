package com.example.editor.transformation.sync;

/** Resumen ligero para seleccionar sesiones sin cargar baselines completos. */
public record TransformationSynchronizationSessionSummary(
        String id,
        String transformationId,
        String transformationEtag,
        boolean stale,
        long revision,
        String sourceModelId,
        String targetModelId,
        long createdAtEpochMillis,
        long updatedAtEpochMillis) {
    /** Descripción: Crea el resumen de una sesión y marca si su baseline pertenece a otra revisión de la transformación. Entradas: session: sesión persistida; currentTransformationEtag: ETag actual de la definición. Salida: devuelve un resumen apto para selección y diagnóstico. */
    /** Descripción: Ejecuta la operación «from» correspondiente a la responsabilidad de TransformationSynchronizationSessionSummary. Entradas: session: dato de entrada necesario para la operación; currentTransformationEtag: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static TransformationSynchronizationSessionSummary from(TransformationSynchronizationSession session, String currentTransformationEtag) {
        String current = currentTransformationEtag == null ? "" : currentTransformationEtag.trim();
        return new TransformationSynchronizationSessionSummary(session.id(), session.transformationId(), session.transformationEtag(),
                !session.transformationEtag().equals(current), session.revision(), session.sourceBaseline().id(), session.targetBaseline().id(),
                session.createdAtEpochMillis(), session.updatedAtEpochMillis());
    }
}
