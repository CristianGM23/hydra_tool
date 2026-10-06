package com.example.editor.transformation.sync;

import java.util.Objects;

import com.example.editor.validation.model.CanonicalModelDocument;

/** Estados actuales de ambos lados; la sesión aporta automáticamente baseline y traza. */
public record TransformationSynchronizationSessionUpdateRequest(
        CanonicalModelDocument sourceModel,
        CanonicalModelDocument targetModel) {
    public TransformationSynchronizationSessionUpdateRequest {
        sourceModel = Objects.requireNonNull(sourceModel, "sourceModel");
        targetModel = Objects.requireNonNull(targetModel, "targetModel");
    }
}
