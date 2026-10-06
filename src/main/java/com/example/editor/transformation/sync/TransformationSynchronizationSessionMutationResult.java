package com.example.editor.transformation.sync;

import com.example.editor.transformation.runtime.TransformationSynchronizationResult;

/** Resultado de sincronizar o restaurar una sesión persistente. */
public record TransformationSynchronizationSessionMutationResult(
        TransformationSynchronizationSession session,
        TransformationSynchronizationResult synchronization,
        boolean advanced,
        Long restoredFromRevision,
        String etag) {
    public TransformationSynchronizationSessionMutationResult {
        etag = etag == null ? "" : etag;
    }
}
