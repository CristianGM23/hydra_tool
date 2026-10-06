package com.example.editor.integration.projection.canonical;

import java.util.List;
import com.example.editor.integration.IntegrationDiagnostic;
import com.example.editor.integration.IntegrationSupportLevel;

/** Resultado de importar una proyección PlantUML o Draw.io. */
public record CanonicalProjectionImportResult(IntegrationSupportLevel supportLevel,
        List<CanonicalProjectionRecoveredArtifact> recoveredArtifacts,
        List<String> opaqueElements, List<IntegrationDiagnostic> diagnostics) {
    public CanonicalProjectionImportResult {
        supportLevel = supportLevel == null ? IntegrationSupportLevel.UNSUPPORTED : supportLevel;
        recoveredArtifacts = recoveredArtifacts == null ? List.of() : List.copyOf(recoveredArtifacts);
        opaqueElements = opaqueElements == null ? List.of() : List.copyOf(opaqueElements);
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }
    /** Descripción: Ejecuta la operación «recovered» correspondiente a la responsabilidad de CanonicalProjectionImportResult. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean recovered(){return !recoveredArtifacts.isEmpty();}
}
