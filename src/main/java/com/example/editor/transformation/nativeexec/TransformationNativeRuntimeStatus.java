package com.example.editor.transformation.nativeexec;

/** Lifecycle outcome of one isolated native transformation bridge execution. */
public enum TransformationNativeRuntimeStatus {
    NOT_RUN,
    SUCCEEDED,
    DISABLED,
    NOT_CONFIGURED,
    CAPACITY_BUSY,
    TOOL_UNAVAILABLE,
    TIMED_OUT,
    FAILED,
    INVALID_RESULT,
    CANCELLED
}
