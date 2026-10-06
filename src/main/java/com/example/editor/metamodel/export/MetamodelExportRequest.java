package com.example.editor.metamodel.export;

/**
 * Describe una descarga individual configurable.
 */
public record MetamodelExportRequest(String format, MetamodelExportOptions options) {

    public MetamodelExportRequest {
        options = options == null ? MetamodelExportOptions.defaults() : options;
    }
}
