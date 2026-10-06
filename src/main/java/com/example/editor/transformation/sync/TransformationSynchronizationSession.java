package com.example.editor.transformation.sync;

import java.util.Objects;

import com.example.editor.contract.StableIdentifierPolicy;
import com.example.editor.transformation.runtime.TransformationTrace;
import com.example.editor.validation.model.CanonicalModelDocument;

/** Estado persistente de una sesión incremental bidireccional. */
public record TransformationSynchronizationSession(
        String schemaVersion,
        String id,
        String transformationId,
        String transformationEtag,
        long revision,
        CanonicalModelDocument sourceBaseline,
        CanonicalModelDocument targetBaseline,
        TransformationTrace baselineTrace,
        long createdAtEpochMillis,
        long updatedAtEpochMillis) {
    public static final String CURRENT_SCHEMA_VERSION = "1.0";

    public TransformationSynchronizationSession {
        schemaVersion = schemaVersion == null || schemaVersion.isBlank() ? CURRENT_SCHEMA_VERSION : schemaVersion.trim();
        if(!CURRENT_SCHEMA_VERSION.equals(schemaVersion)) throw new IllegalArgumentException("Unsupported synchronization session schemaVersion: " + schemaVersion);
        id = StableIdentifierPolicy.requireDocumentId(id, "synchronizationSession.id");
        transformationId = StableIdentifierPolicy.requireDocumentId(transformationId, "synchronizationSession.transformationId");
        transformationEtag = transformationEtag == null ? "" : transformationEtag.trim();
        if(transformationEtag.isBlank()) throw new IllegalArgumentException("synchronizationSession.transformationEtag is required");
        if(revision < 1) throw new IllegalArgumentException("synchronizationSession.revision must be at least 1");
        sourceBaseline = Objects.requireNonNull(sourceBaseline, "sourceBaseline");
        targetBaseline = Objects.requireNonNull(targetBaseline, "targetBaseline");
        baselineTrace = Objects.requireNonNull(baselineTrace, "baselineTrace");
        if(createdAtEpochMillis <= 0 || updatedAtEpochMillis <= 0) throw new IllegalArgumentException("Synchronization session timestamps must be positive");
        if(updatedAtEpochMillis < createdAtEpochMillis) throw new IllegalArgumentException("updatedAtEpochMillis cannot be before createdAtEpochMillis");
    }
/** Descripción: Inicializa una instancia de TransformationSynchronizationSession con sus dependencias y datos de entrada. Entradas: schemaVersion: dato de entrada necesario para la operación; id: identificador que delimita el recurso objetivo; transformationId: identificador que delimita el recurso objetivo; revision: dato de entrada necesario para la operación; sourceBaseline: dato de entrada necesario para la operación; targetBaseline: dato de entrada necesario para la operación; baselineTrace: dato de entrada necesario para la operación; createdAtEpochMillis: dato de entrada necesario para la operación; updatedAtEpochMillis: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public TransformationSynchronizationSession(String schemaVersion, String id, String transformationId, long revision,
            CanonicalModelDocument sourceBaseline, CanonicalModelDocument targetBaseline, TransformationTrace baselineTrace,
            long createdAtEpochMillis, long updatedAtEpochMillis) {
        this(schemaVersion, id, transformationId, "legacy-compatible", revision, sourceBaseline, targetBaseline, baselineTrace,
                createdAtEpochMillis, updatedAtEpochMillis);
    }

}
