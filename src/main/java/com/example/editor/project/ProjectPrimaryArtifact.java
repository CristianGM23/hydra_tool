package com.example.editor.project;

/** Artefacto principal opcional que da contexto a Home, preview, ejecución y generación. */
public record ProjectPrimaryArtifact(String kind,String id,String relativePath) {
    public ProjectPrimaryArtifact {
        kind = kind == null ? "" : kind.trim();
        id = id == null ? "" : id.trim();
        relativePath = relativePath == null ? "" : relativePath.trim().replace('\\','/');
        if(relativePath.startsWith("/") || relativePath.contains("../") || relativePath.equals("..")) {
            throw new IllegalArgumentException("Primary artifact path must be a safe project-relative path");
        }
    }
    /** Descripción: Ejecuta la operación «defined» correspondiente a la responsabilidad de ProjectPrimaryArtifact. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean defined() { return !kind.isBlank() || !id.isBlank() || !relativePath.isBlank(); }
}
