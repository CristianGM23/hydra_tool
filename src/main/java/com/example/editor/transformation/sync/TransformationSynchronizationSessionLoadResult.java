package com.example.editor.transformation.sync;

/** Documento de sesión junto con su ETag para actualizaciones optimistas. */
public record TransformationSynchronizationSessionLoadResult(
        TransformationSynchronizationSession session,
        String etag) {
    public TransformationSynchronizationSessionLoadResult {
        etag = etag == null ? "" : etag;
    }
}
