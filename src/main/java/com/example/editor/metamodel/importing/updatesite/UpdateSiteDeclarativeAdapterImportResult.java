package com.example.editor.metamodel.importing.updatesite;

/** Resultado de registrar de forma persistente un adaptador validado. */
public record UpdateSiteDeclarativeAdapterImportResult(UpdateSiteDeclarativeAdapterValidation validation,
        boolean persisted, boolean replaced, String fileName) {
    public UpdateSiteDeclarativeAdapterImportResult {
        fileName = fileName == null ? "" : fileName.trim();
    }
}
