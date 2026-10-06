package com.example.editor.performance;

import java.util.List;

/** Violations and actionable chunk/cache recommendations. */
public record CapabilityPerformanceEvaluation(boolean passed, List<String> violations,
        List<String> recommendations) {
    public CapabilityPerformanceEvaluation {
        violations = violations == null ? List.of() : List.copyOf(violations);
        recommendations = recommendations == null ? List.of() : List.copyOf(recommendations);
    }
}
