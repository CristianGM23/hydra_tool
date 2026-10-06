package com.example.editor.integration.projection;
import java.util.List;
import com.example.editor.integration.IntegrationDiagnostic;
import com.example.editor.integration.IntegrationSupportLevel;
/** Análisis sin ejecución de un archivo externo que podría importarse. */
public record ProjectionImportAnalysis(IntegrationSupportLevel supportLevel, String detectedVersion,
        List<String> detectedElements, List<String> opaqueElements, List<IntegrationDiagnostic> diagnostics) {
    public ProjectionImportAnalysis {
        if(supportLevel == null) throw new IllegalArgumentException("supportLevel is required");
        detectedVersion = detectedVersion == null ? "" : detectedVersion.trim();
        detectedElements = detectedElements == null ? List.of() : List.copyOf(detectedElements);
        opaqueElements = opaqueElements == null ? List.of() : List.copyOf(opaqueElements);
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }
}
