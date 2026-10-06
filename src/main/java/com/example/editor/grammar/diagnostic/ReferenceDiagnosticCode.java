package com.example.editor.grammar.diagnostic;

/** Clasificación neutral de fallos de referencias cruzadas entre tecnologías de gramáticas. */
public enum ReferenceDiagnosticCode {
    REFERENCE_NOT_FOUND,
    REFERENCE_NOT_VISIBLE,
    REFERENCE_WRONG_TYPE,
    REFERENCE_AMBIGUOUS,
    REFERENCE_IMPORT_MISSING,
    REFERENCE_MAPPING_MISMATCH,
    REFERENCE_CYCLE,
    REFERENCE_TARGET_DELETED,
    REFERENCE_CASE_MISMATCH,
    REFERENCE_STALE
}
