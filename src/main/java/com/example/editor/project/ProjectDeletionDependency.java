package com.example.editor.project;

/** Resumen acotado de un bloque de artefactos que pertenece al proyecto activo. */
public record ProjectDeletionDependency(
        String path,
        String artifactKind,
        int entries,
        long bytes
) {
    public ProjectDeletionDependency {
        path = path == null ? "" : path;
        artifactKind = artifactKind == null ? "GENERIC_FILE" : artifactKind;
        entries = Math.max(0, entries);
        bytes = Math.max(0L, bytes);
    }
}
