package com.example.editor.integration.parity;

import java.util.List;
import java.util.Objects;

/** Evidence for one neutral capability. Never synthesizes missing runtime tests. */
public record NativeParityCapabilityResult(NativeParityCapability capability,
        NativeParityEvidenceStatus status, List<String> passedProofs,
        List<String> failedProofs, String explanation) {
    public NativeParityCapabilityResult {
        Objects.requireNonNull(capability, "capability");
        Objects.requireNonNull(status, "status");
        passedProofs = List.copyOf(Objects.requireNonNull(passedProofs, "passedProofs"));
        failedProofs = List.copyOf(Objects.requireNonNull(failedProofs, "failedProofs"));
        explanation = Objects.requireNonNull(explanation, "explanation");
    }
}
