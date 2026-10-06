package com.example.editor.transformation.core;

/** Comportamiento explícito cuando una adaptación de valor no puede completarse. */
public enum TransformationBindingErrorPolicy {
    FAIL,
    SKIP_VALUE,
    USE_FALLBACK
}
