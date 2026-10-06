package com.example.editor.transformation.nativeexec;

/** Outcome of a trusted toolchain provisioning mutation. */
public enum TransformationToolchainProvisioningStatus {
    INSTALLED,
    UPDATED,
    REMOVED,
    NO_CHANGE,
    BLOCKED,
    ROLLED_BACK
}
