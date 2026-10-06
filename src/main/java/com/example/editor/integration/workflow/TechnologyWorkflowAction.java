package com.example.editor.integration.workflow;

/** Acción principal de HYDRA asociada a una fase del recorrido tecnológico. */
public enum TechnologyWorkflowAction {
    EDIT_PROFILE,
    CHECK_PROFILE,
    COMPILE_ARCHIVE,
    VALIDATE_PROJECT,
    PREVIEW_CHANGES,
    APPLY_CHANGES,
    RUN_NATIVE_HOST,
    ROUND_TRIP_SYNC,
    EXPORT_PROJECT,
    EXPORT_PROJECTION,
    NONE
}
