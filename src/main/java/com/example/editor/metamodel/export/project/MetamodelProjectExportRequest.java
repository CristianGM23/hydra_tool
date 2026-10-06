package com.example.editor.metamodel.export.project;

/** Petición HTTP de una exportación tecnológica completa. */
public record MetamodelProjectExportRequest(MetamodelProjectExportOptions options) {
    public MetamodelProjectExportRequest {if(options == null){options = MetamodelProjectExportOptions.defaults();}}
}
