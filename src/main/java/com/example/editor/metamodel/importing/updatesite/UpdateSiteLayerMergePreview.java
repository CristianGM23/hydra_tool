package com.example.editor.metamodel.importing.updatesite;

import java.util.List;

import com.example.editor.definition.EditorDefinitionCheckReport;
import com.example.editor.definition.EditorDefinitionModel;

/** Preview estructurado de la fusión, validado contra el metamodelo de destino. */
public record UpdateSiteLayerMergePreview(String transactionId, String targetMetamodelId,
        String targetEditorId, String originalEtag, EditorDefinitionModel mergedEditorDefinition,
        EditorDefinitionCheckReport checkReport, List<UpdateSiteLayerMergeItem> items,
        int keptElements, int recoveredElements, int skippedElements,
        boolean applicable, List<String> diagnostics) {
    public UpdateSiteLayerMergePreview {
        transactionId = text(transactionId); targetMetamodelId = text(targetMetamodelId);
        targetEditorId = text(targetEditorId); originalEtag = text(originalEtag);
        items = items == null ? List.of() : List.copyOf(items);
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }
    /** Descripción: Ejecuta la operación «text» correspondiente a la responsabilidad de UpdateSiteLayerMergePreview. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String text(String value) { return value == null ? "" : value.trim(); }
}
