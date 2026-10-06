package com.example.editor.integration.roundtrip.resolution;

/** Explicit user or policy decision for one round-trip conflict. */
public enum ConflictResolutionChoice {
    KEEP_HYDRA,
    KEEP_NATIVE,
    KEEP_MANUAL,
    MERGE_SAFE,
    SKIP
}
