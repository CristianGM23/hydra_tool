package com.example.editor.transformation.parity;

/** Estado verificable de paridad entre la ejecución canónica y una ejecución nativa externa. */
public enum TransformationParityStatus {
    MATCHED,
    MISMATCHED,
    EXPORT_BLOCKED,
    INVALID_EVIDENCE
}
