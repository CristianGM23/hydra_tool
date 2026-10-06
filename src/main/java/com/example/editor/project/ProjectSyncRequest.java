package com.example.editor.project;

/** Petición para previsualizar o aplicar una sincronización. */
public record ProjectSyncRequest(WorkspaceTransferDirection direction,WorkspaceTransferMode mode) {
    public ProjectSyncRequest {
        direction = direction == null ? WorkspaceTransferDirection.WORKSPACE_TO_PROJECT : direction;
        mode = mode == null ? WorkspaceTransferMode.MERGE : mode;
    }
}
