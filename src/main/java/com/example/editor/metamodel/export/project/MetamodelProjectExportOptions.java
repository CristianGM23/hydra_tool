package com.example.editor.metamodel.export.project;

import java.util.List;
import java.util.Map;

/** Opciones neutrales para generar un workspace tecnológico completo. */
public record MetamodelProjectExportOptions(boolean includeExistingLayers, MetamodelProjectArtifactSelectionMode selectionMode,
        List<MetamodelProjectArtifactRef> selectedArtifacts, String editorId, Map<String, String> settings) {
    public MetamodelProjectExportOptions {
        selectionMode = selectionMode == null ? MetamodelProjectArtifactSelectionMode.ALL_COMPATIBLE : selectionMode;
        selectedArtifacts = selectedArtifacts == null ? List.of() : List.copyOf(selectedArtifacts);
        editorId = editorId == null ? "" : editorId.trim();
        settings = settings == null ? Map.of() : Map.copyOf(settings);
    }

    /** Descripción: Ejecuta la operación «defaults» correspondiente a la responsabilidad de MetamodelProjectExportOptions. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public static MetamodelProjectExportOptions defaults(){
        return new MetamodelProjectExportOptions(true, MetamodelProjectArtifactSelectionMode.ALL_COMPATIBLE, List.of(), "", Map.of());
    }
}
