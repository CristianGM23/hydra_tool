package com.example.editor.project;

/** Petición para abrir un proyecto existente. */
public record OpenLocalProjectRequest(String projectDirectory,boolean loadIntoCurrentWorkspace,WorkspaceTransferMode transferMode) {
    public OpenLocalProjectRequest { transferMode = transferMode == null ? WorkspaceTransferMode.MERGE : transferMode; }
}
