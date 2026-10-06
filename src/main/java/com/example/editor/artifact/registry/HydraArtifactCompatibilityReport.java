package com.example.editor.artifact.registry;

import java.util.List;

/** Informe de compatibilidad de un artefacto frente a las versiones y estados de sus dependencias. */
public record HydraArtifactCompatibilityReport(String artifactId, HydraArtifactCompatibilityStatus status,
        List<String> diagnostics) {
    public HydraArtifactCompatibilityReport {
        artifactId = artifactId == null ? "" : artifactId.trim();
        status = status == null ? HydraArtifactCompatibilityStatus.COMPATIBLE : status;
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }
}
