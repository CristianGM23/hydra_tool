package com.example.editor.transformation.core;

/** Deterministic policy reserved for bidirectional synchronization conflicts. */
public enum TransformationConflictPolicy {
    FAIL_ON_CONFLICT,
    FORWARD_WINS,
    REVERSE_WINS
}
