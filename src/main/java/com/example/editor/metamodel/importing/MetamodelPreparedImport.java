package com.example.editor.metamodel.importing;

/** Resultado canónico reutilizable de un análisis completado, listo para persistencia sin reparsear la fuente. */
public record MetamodelPreparedImport(String jobId, MetamodelImportPreview preview, MetamodelImportOptions options, String sourceSha256) {
    public MetamodelPreparedImport {
        if(jobId == null || jobId.isBlank()){throw new IllegalArgumentException("jobId is required");}
        if(preview == null){throw new IllegalArgumentException("preview is required");}
        options = options == null ? MetamodelImportOptions.defaults() : options;
        sourceSha256 = sourceSha256 == null ? "" : sourceSha256.trim();
    }
}
