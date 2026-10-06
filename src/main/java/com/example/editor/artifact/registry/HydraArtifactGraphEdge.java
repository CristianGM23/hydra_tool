package com.example.editor.artifact.registry;

/** Arista tipada materializada en la proyección del grafo de artefactos. */
public record HydraArtifactGraphEdge(String fromArtifactId, String toArtifactId,
        HydraArtifactRelationKind relationKind, boolean required, String minimumVersion) {
    public HydraArtifactGraphEdge {
        fromArtifactId = text(fromArtifactId);
        toArtifactId = text(toArtifactId);
        relationKind = relationKind == null ? HydraArtifactRelationKind.USES : relationKind;
        minimumVersion = text(minimumVersion);
    }

    /** Descripción: Normaliza identificadores y versiones de una arista. Entradas: value: texto de entrada. Salida: cadena no nula. */
    private static String text(String value) { return value == null ? "" : value.trim(); }
}
