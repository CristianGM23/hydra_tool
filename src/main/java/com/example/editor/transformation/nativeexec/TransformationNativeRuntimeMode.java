package com.example.editor.transformation.nativeexec;

/**
 * Describes what an external toolchain execution proves today.
 * NATIVE_EXPORT means the exported artefact has independent external semantics;
 * CANONICAL_BUNDLE means the export still delegates to HYDRA's canonical runtime.
 */
public enum TransformationNativeRuntimeMode {
    NATIVE_EXPORT,
    CANONICAL_BUNDLE
}
