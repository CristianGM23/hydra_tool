package com.example.editor.integration.roundtrip.resolution;

import java.util.Objects;

/** One conflict paired with its validated resolution. */
public record ResolvedElementConflict(ElementConflict conflict, ElementConflictDecision decision) {
    public ResolvedElementConflict {
        conflict = Objects.requireNonNull(conflict, "conflict");
        decision = Objects.requireNonNull(decision, "decision");
    }
}
