package com.example.editor.metamodel.importing.updatesite;

import java.util.List;

/** Entrada explicativa y auditable del flujo de importación. */
public record UpdateSiteImportLogEntry(String id, UpdateSiteImportStage stage, UpdateSiteLogSeverity severity,
        UpdateSiteFileStatus status, String title, String message, String sourcePath, String artifactKind,
        String purpose, String recoveredAs, String nextAction, UpdateSiteConfidence confidence,
        List<String> details) {
    public UpdateSiteImportLogEntry {
        id = text(id);
        stage = stage == null ? UpdateSiteImportStage.SOURCE : stage;
        severity = severity == null ? UpdateSiteLogSeverity.INFO : severity;
        status = status == null ? UpdateSiteFileStatus.SUPPORTING : status;
        title = text(title);
        message = text(message);
        sourcePath = text(sourcePath);
        artifactKind = text(artifactKind);
        purpose = text(purpose);
        recoveredAs = text(recoveredAs);
        nextAction = text(nextAction);
        confidence = confidence == null ? UpdateSiteConfidence.INFERRED : confidence;
        details = details == null ? List.of() : List.copyOf(details);
    }

    /** Descripción: Ejecuta la operación «text» correspondiente a la responsabilidad de UpdateSiteImportLogEntry. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String text(String value) { return value == null ? "" : value.trim(); }
}
