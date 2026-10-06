package com.example.editor.transformation.export;

import java.util.List;

/** Resultado autocontenido de compatibilidad y generación para una tecnología de destino. */
public record TransformationExportResult(String transformationId,TransformationExportLanguage language,
        TransformationExportFidelity fidelity,boolean compatible,List<TransformationExportDiagnostic> diagnostics,
        List<TransformationExportArtifact> artifacts) {
    public TransformationExportResult {
        transformationId=transformationId==null?"":transformationId.trim();
        diagnostics=diagnostics==null?List.of():List.copyOf(diagnostics);
        artifacts=artifacts==null?List.of():List.copyOf(artifacts);
    }
}
