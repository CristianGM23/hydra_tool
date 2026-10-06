package com.example.editor.integration.verification;

/** Estado de una comprobación individual. */
public enum TechnologyValidationStepStatus {
    PASSED,
    FAILED,
    SKIPPED,
    TOOL_UNAVAILABLE,
    TIMED_OUT,
    CANCELLED
}
