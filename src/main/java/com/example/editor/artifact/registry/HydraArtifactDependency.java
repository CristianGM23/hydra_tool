package com.example.editor.artifact.registry;

import java.util.Objects;

/** Dependencia dirigida y tipada desde un artefacto hacia otro. */
public record HydraArtifactDependency(String artifactId, boolean required, String minimumVersion,
        HydraArtifactRelationKind relationKind) {
    public HydraArtifactDependency {
        artifactId = Objects.requireNonNullElse(artifactId, "").trim();
        minimumVersion = Objects.requireNonNullElse(minimumVersion, "").trim();
        relationKind = relationKind == null ? HydraArtifactRelationKind.USES : relationKind;
        if (artifactId.isBlank()) throw new IllegalArgumentException("artifactId must not be blank");
    }

    /** Descripción: Mantiene compatibilidad con descriptores anteriores que no declaraban el tipo de relación. Entradas: artifactId: destino; required: obligatoriedad; minimumVersion: versión mínima. Salida: crea una dependencia USES. */
    public HydraArtifactDependency(String artifactId, boolean required, String minimumVersion) {
        this(artifactId, required, minimumVersion, HydraArtifactRelationKind.USES);
    }
}
