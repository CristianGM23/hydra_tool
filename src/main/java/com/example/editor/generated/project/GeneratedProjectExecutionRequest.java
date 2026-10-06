package com.example.editor.generated.project;

import java.util.List;

/** Lifecycle execution request selecting stages, dry-run behavior and optional launch permission. */
public record GeneratedProjectExecutionRequest(String projectId, List<GeneratedProjectStage> stages,
        boolean dryRun, boolean allowOptionalStages, boolean continueAfterFailure) {
    public GeneratedProjectExecutionRequest {
        projectId = projectId == null ? "" : projectId.trim();
        stages = stages == null ? List.of() : stages.stream().distinct()
                .sorted(java.util.Comparator.comparing(Enum::ordinal)).toList();
        if(projectId.isBlank()){throw new IllegalArgumentException("projectId must not be blank");}
    }
}
