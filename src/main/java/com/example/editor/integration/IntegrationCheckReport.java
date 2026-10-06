package com.example.editor.integration;
import java.util.List;
/** Resultado estructural y de compatibilidad de un perfil. */
public record IntegrationCheckReport(String profileId, List<IntegrationDiagnostic> diagnostics, List<IntegrationArtifactResolution> artifactResolutions) {
    public IntegrationCheckReport {
        profileId = profileId == null ? "" : profileId;
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
        artifactResolutions = artifactResolutions == null ? List.of() : List.copyOf(artifactResolutions);
    }
    /** Descripción: Ejecuta la operación «valid» correspondiente a la responsabilidad de IntegrationCheckReport. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean valid() { return diagnostics.stream().noneMatch(value -> value.severity() == IntegrationDiagnosticSeverity.ERROR); }
}
