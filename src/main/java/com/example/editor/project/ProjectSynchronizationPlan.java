package com.example.editor.project;

import java.util.List;

/** Previsualización segura de una sincronización. */
public record ProjectSynchronizationPlan(WorkspaceTransferDirection direction,WorkspaceTransferMode mode,
        List<ProjectSyncFileChange> changes,List<String> warnings,boolean executable) {
    public ProjectSynchronizationPlan {
        changes = changes == null ? List.of() : List.copyOf(changes);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
