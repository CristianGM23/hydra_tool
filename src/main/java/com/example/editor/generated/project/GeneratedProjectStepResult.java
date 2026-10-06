package com.example.editor.generated.project;

import java.util.List;

/** Auditable result for one lifecycle stage. */
public record GeneratedProjectStepResult(GeneratedProjectStage stage, GeneratedProjectStepStatus status,
        String description, List<String> command, Integer exitCode, long durationMillis,
        String summary, List<String> diagnostics) {
    public GeneratedProjectStepResult {
        command = command == null ? List.of() : List.copyOf(command);
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
        description = description == null ? "" : description;
        summary = summary == null ? "" : summary;
    }
}
