package com.example.editor.security.process;

/** Estado neutral de una ejecución de proceso externo controlada por HYDRA. */
public enum HydraExternalProcessRunStatus {
    COMPLETED,
    REJECTED,
    TOOL_UNAVAILABLE,
    TIMED_OUT,
    CANCELLED,
    FAILED
}
