package com.example.editor.transformation.nativeexec;

/** Identifies where HYDRA obtained the server-owned command for a transformation runtime. */
public enum TransformationNativeRuntimeSource {
    NONE,
    EXPLICIT_COMMAND,
    MANAGED_BUNDLE
}
