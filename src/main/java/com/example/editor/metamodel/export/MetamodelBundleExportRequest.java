package com.example.editor.metamodel.export;

import java.util.List;

/**
 * Describe un paquete de formatos seleccionados.
 */
public record MetamodelBundleExportRequest(List<String> formats, MetamodelExportOptions options) {

    public MetamodelBundleExportRequest {
        formats = formats == null ? List.of() : List.copyOf(formats);
        options = options == null ? MetamodelExportOptions.defaults() : options;
    }
}
