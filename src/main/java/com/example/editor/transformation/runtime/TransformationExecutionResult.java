package com.example.editor.transformation.runtime;

import com.example.editor.validation.model.CanonicalModelDocument;

/** Resultado canónico de una ejecución M2M batch. */
public record TransformationExecutionResult(CanonicalModelDocument targetModel, TransformationTrace trace) {
}
