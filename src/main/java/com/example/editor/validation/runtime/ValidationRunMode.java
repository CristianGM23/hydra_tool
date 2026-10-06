package com.example.editor.validation.runtime;

/** Modo solicitado al ejecutar un plan de validaciones. */
public enum ValidationRunMode {
    FULL, INCREMENTAL, SELECTION, CLASS, MANUAL, ON_OPEN, ON_SAVE, BEFORE_EXPORT
}
