package com.example.editor.project;

/** Solicitud para copiar un proyecto local existente dentro de otro directorio. */
public record ImportLocalProjectDirectoryRequest(String sourceDirectory,String parentDirectory,String projectName) {
    public ImportLocalProjectDirectoryRequest {
        sourceDirectory = sourceDirectory == null ? "" : sourceDirectory.trim();
        parentDirectory = parentDirectory == null ? "" : parentDirectory.trim();
        projectName = projectName == null ? "" : projectName.trim();
    }
}
