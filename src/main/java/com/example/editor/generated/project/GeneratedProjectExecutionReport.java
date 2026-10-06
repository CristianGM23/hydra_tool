package com.example.editor.generated.project;

import java.time.Instant;
import java.util.List;

/** Complete lifecycle report for a generated project. */
public record GeneratedProjectExecutionReport(String runId, GeneratedProjectDefinition project,
        String resolvedDirectory, Instant startedAt, Instant completedAt,
        List<GeneratedProjectStepResult> steps, boolean successful) {
    public GeneratedProjectExecutionReport {
        steps = steps == null ? List.of() : List.copyOf(steps);
    }
}
