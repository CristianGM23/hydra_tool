package com.example.editor.portability.merge;

/** Explicit choice for one semantic entity during a deep multi-layer merge. */
public enum CapabilitySemanticMergeDecision {
    KEEP_EXISTING,
    USE_RECOVERED,
    MERGE,
    SKIP
}
