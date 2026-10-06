package com.example.editor.transformation.sync;

import com.example.editor.transformation.runtime.TransformationSynchronizationSide;
import com.example.editor.validation.model.CanonicalModelDocument;

/** Inicializa una sesión a partir de uno de los lados y crea una baseline sincronizada verificable. */
public record TransformationSynchronizationSessionCreateRequest(
        CanonicalModelDocument sourceModel,
        CanonicalModelDocument targetModel,
        TransformationSynchronizationSide initializeFrom) {
    public TransformationSynchronizationSessionCreateRequest {
        initializeFrom = initializeFrom == null ? TransformationSynchronizationSide.SOURCE : initializeFrom;
        if(initializeFrom == TransformationSynchronizationSide.BOTH)
            throw new IllegalArgumentException("Session initialization must choose SOURCE or TARGET as the authoritative side");
    }
}
