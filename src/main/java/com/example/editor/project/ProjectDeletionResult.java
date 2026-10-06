package com.example.editor.project;

/** Resultado inmutable del borrado completo de un proyecto local. */
public record ProjectDeletionResult(
        String projectId,
        String projectName,
        String directory,
        int deletedEntries,
        long deletedBytes
) {
    public ProjectDeletionResult {
        projectId = projectId == null ? "" : projectId;
        projectName = projectName == null ? "" : projectName;
        directory = directory == null ? "" : directory;
        deletedEntries = Math.max(0, deletedEntries);
        deletedBytes = Math.max(0L, deletedBytes);
    }
}
