package com.example.editor.integration.roundtrip;

/** Clasificación estable de cambios entre HYDRA y un proyecto tecnológico. */
public enum RoundTripChangeKind {
    UNCHANGED,
    ADD,
    REGENERATE,
    MANUAL_CONFLICT,
    NATIVE_DELETED,
    ORPHAN_NATIVE
}
