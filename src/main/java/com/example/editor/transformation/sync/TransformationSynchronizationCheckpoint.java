package com.example.editor.transformation.sync;

import java.util.List;
import java.util.Objects;

import com.example.editor.contract.StableIdentifierPolicy;
import com.example.editor.transformation.core.TransformationExecutionDirection;
import com.example.editor.transformation.runtime.TransformationModelDelta;
import com.example.editor.transformation.runtime.TransformationSynchronizationConflict;
import com.example.editor.transformation.runtime.TransformationTrace;
import com.example.editor.validation.model.CanonicalModelDocument;

/** Checkpoint inmutable de una baseline aceptada dentro de una sesión de sincronización. */
public record TransformationSynchronizationCheckpoint(
        String schemaVersion,
        String id,
        String sessionId,
        String transformationId,
        String transformationEtag,
        long revision,
        CanonicalModelDocument sourceBaseline,
        CanonicalModelDocument targetBaseline,
        TransformationTrace baselineTrace,
        String operation,
        String conflictResolution,
        List<TransformationExecutionDirection> appliedDirections,
        TransformationModelDelta sourceDelta,
        TransformationModelDelta targetDelta,
        List<TransformationSynchronizationConflict> conflicts,
        long createdAtEpochMillis) {
    public static final String CURRENT_SCHEMA_VERSION = "1.0";

    public TransformationSynchronizationCheckpoint {
        schemaVersion = schemaVersion == null || schemaVersion.isBlank() ? CURRENT_SCHEMA_VERSION : schemaVersion.trim();
        if(!CURRENT_SCHEMA_VERSION.equals(schemaVersion)) throw new IllegalArgumentException("Unsupported synchronization checkpoint schemaVersion: " + schemaVersion);
        id = StableIdentifierPolicy.requireDocumentId(id, "synchronizationCheckpoint.id");
        sessionId = StableIdentifierPolicy.requireDocumentId(sessionId, "synchronizationCheckpoint.sessionId");
        transformationId = StableIdentifierPolicy.requireDocumentId(transformationId, "synchronizationCheckpoint.transformationId");
        transformationEtag = transformationEtag == null ? "" : transformationEtag.trim();
        if(transformationEtag.isBlank()) throw new IllegalArgumentException("synchronizationCheckpoint.transformationEtag is required");
        if(revision < 1) throw new IllegalArgumentException("synchronizationCheckpoint.revision must be at least 1");
        sourceBaseline = Objects.requireNonNull(sourceBaseline, "sourceBaseline");
        targetBaseline = Objects.requireNonNull(targetBaseline, "targetBaseline");
        baselineTrace = Objects.requireNonNull(baselineTrace, "baselineTrace");
        operation = operation == null ? "" : operation.trim();
        conflictResolution = conflictResolution == null ? "" : conflictResolution.trim();
        appliedDirections = appliedDirections == null ? List.of() : List.copyOf(appliedDirections);
        sourceDelta = sourceDelta == null ? TransformationModelDelta.empty() : sourceDelta;
        targetDelta = targetDelta == null ? TransformationModelDelta.empty() : targetDelta;
        conflicts = conflicts == null ? List.of() : List.copyOf(conflicts);
        if(createdAtEpochMillis <= 0) throw new IllegalArgumentException("createdAtEpochMillis must be positive");
    }

    /** Descripción: Valida las condiciones necesarias para «checkpointId» y rechaza entradas no válidas. Entradas: sessionId: identificador que delimita el recurso objetivo; revision: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static String checkpointId(String sessionId, long revision) {
        StableIdentifierPolicy.requireDocumentId(sessionId, "sessionId");
        if(revision < 1) throw new IllegalArgumentException("revision must be at least 1");
        return sessionId + ".r" + revision;
    }
/** Descripción: Inicializa una instancia de TransformationSynchronizationCheckpoint con sus dependencias y datos de entrada. Entradas: schemaVersion: dato de entrada necesario para la operación; id: identificador que delimita el recurso objetivo; sessionId: identificador que delimita el recurso objetivo; transformationId: identificador que delimita el recurso objetivo; revision: dato de entrada necesario para la operación; sourceBaseline: dato de entrada necesario para la operación; targetBaseline: dato de entrada necesario para la operación; baselineTrace: dato de entrada necesario para la operación; operation: dato de entrada necesario para la operación; conflictResolution: dato de entrada necesario para la operación; appliedDirections: dato de entrada necesario para la operación; sourceDelta: dato de entrada necesario para la operación; targetDelta: dato de entrada necesario para la operación; conflicts: dato de entrada necesario para la operación; createdAtEpochMillis: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public TransformationSynchronizationCheckpoint(String schemaVersion, String id, String sessionId, String transformationId, long revision,
            CanonicalModelDocument sourceBaseline, CanonicalModelDocument targetBaseline, TransformationTrace baselineTrace,
            String operation, String conflictResolution, List<TransformationExecutionDirection> appliedDirections,
            TransformationModelDelta sourceDelta, TransformationModelDelta targetDelta,
            List<TransformationSynchronizationConflict> conflicts, long createdAtEpochMillis) {
        this(schemaVersion, id, sessionId, transformationId, "legacy-compatible", revision, sourceBaseline, targetBaseline, baselineTrace,
                operation, conflictResolution, appliedDirections, sourceDelta, targetDelta, conflicts, createdAtEpochMillis);
    }

}
