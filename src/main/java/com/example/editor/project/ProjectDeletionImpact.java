package com.example.editor.project;

import java.util.List;

/** Impacto de eliminar el directorio portable completo del proyecto activo. */
public record ProjectDeletionImpact(
        String projectId,
        String projectName,
        String directory,
        int fileCount,
        int directoryCount,
        long totalBytes,
        List<ProjectDeletionDependency> dependencies
) {
    public ProjectDeletionImpact {
        projectId = projectId == null ? "" : projectId;
        projectName = projectName == null ? "" : projectName;
        directory = directory == null ? "" : directory;
        fileCount = Math.max(0, fileCount);
        directoryCount = Math.max(0, directoryCount);
        totalBytes = Math.max(0L, totalBytes);
        dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
    }

    /** Descripción: Evalúa la condición «hasDependencies» sin modificar el estado externo. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean hasDependencies() { return !dependencies.isEmpty(); }
}
