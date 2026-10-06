package com.example.editor.transformation.nativeexec;

/** Public, path-free health state for one managed transformation toolchain. */
public enum TransformationNativeRuntimeHealth {
    DISABLED,
    NOT_CONFIGURED,
    CONFIGURATION_INVALID,
    UNVERIFIED,
    READY,
    TOOL_UNAVAILABLE,
    INCOMPATIBLE,
    FAILED
}
