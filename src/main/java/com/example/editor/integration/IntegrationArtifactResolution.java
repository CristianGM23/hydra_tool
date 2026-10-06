package com.example.editor.integration;
/** Resultado de resolver un documento seleccionado. */
public record IntegrationArtifactResolution(IntegrationArtifactSelection artifact, IntegrationResolutionStatus status, String message) {
    public IntegrationArtifactResolution {
        if(artifact == null || status == null) throw new IllegalArgumentException("artifact and status are required");
        message = message == null ? "" : message.trim();
    }
}
