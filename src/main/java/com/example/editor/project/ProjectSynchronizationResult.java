package com.example.editor.project;

import java.time.Instant;
import java.util.List;

/** Resultado de una sincronización aplicada. */
public record ProjectSynchronizationResult(WorkspaceTransferDirection direction,WorkspaceTransferMode mode,
        Instant completedAt,List<ProjectSyncFileChange> appliedChanges,List<String> warnings) {
    public ProjectSynchronizationResult {
        appliedChanges = appliedChanges == null ? List.of() : List.copyOf(appliedChanges);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
