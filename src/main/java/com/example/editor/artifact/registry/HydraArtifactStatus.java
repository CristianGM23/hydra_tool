package com.example.editor.artifact.registry;

/** Synchronization and availability state of a registered artifact. */
public enum HydraArtifactStatus {
    AVAILABLE,
    STALE,
    MISSING_DEPENDENCY,
    INCOMPATIBLE_DEPENDENCY,
    CONFLICTED,
    INVALID
}
