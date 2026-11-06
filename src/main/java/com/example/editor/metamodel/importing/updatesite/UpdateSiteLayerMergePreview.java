package com.example.editor.metamodel.importing.updatesite;

import java.util.List;

import com.example.editor.definition.EditorDefinitionCheckReport;
import com.example.editor.definition.EditorDefinitionModel;

/**
 * Preview estructurado de la fusión, validado contra el metamodelo de destino.
 * {@code unresolvedConflictKeys} enumera las colisiones sin decisión explícita; mientras haya alguna la fusión no es aplicable.
 */
public record UpdateSiteLayerMergePreview(String transactionId, String targetMetamodelId,
        String targetEditorId, String originalEtag, EditorDefinitionModel mergedEditorDefinition,
        EditorDefinitionCheckReport checkReport, List<UpdateSiteLayerMergeItem> items,
        int keptElements, int recoveredElements, int skippedElements,
        boolean applicable, List<String> diagnostics, List<String> unresolvedConflictKeys) {
    /** Descripción: Inicializa una instancia de UpdateSiteLayerMergePreview con sus dependencias y datos de entrada. Entradas: transactionId: identificador que delimita el recurso objetivo; targetMetamodelId: identificador que delimita el recurso objetivo; targetEditorId: identificador que delimita el recurso objetivo; originalEtag: dato de entrada necesario para la operación; mergedEditorDefinition: dato de entrada necesario para la operación; checkReport: dato de entrada necesario para la operación; items: colección de elementos que debe procesarse; keptElements: dato de entrada necesario para la operación; recoveredElements: dato de entrada necesario para la operación; skippedElements: dato de entrada necesario para la operación; applicable: dato de entrada necesario para la operación; diagnostics: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public UpdateSiteLayerMergePreview(String transactionId, String targetMetamodelId,
            String targetEditorId, String originalEtag, EditorDefinitionModel mergedEditorDefinition,
            EditorDefinitionCheckReport checkReport, List<UpdateSiteLayerMergeItem> items,
            int keptElements, int recoveredElements, int skippedElements,
            boolean applicable, List<String> diagnostics) {
        this(transactionId, targetMetamodelId, targetEditorId, originalEtag, mergedEditorDefinition, checkReport, items,
                keptElements, recoveredElements, skippedElements, applicable, diagnostics, List.of());
    }
    public UpdateSiteLayerMergePreview {
        transactionId = text(transactionId); targetMetamodelId = text(targetMetamodelId);
        targetEditorId = text(targetEditorId); originalEtag = text(originalEtag);
        items = items == null ? List.of() : List.copyOf(items);
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
        unresolvedConflictKeys = unresolvedConflictKeys == null ? List.of() : List.copyOf(unresolvedConflictKeys);
    }
    /** Descripción: Ejecuta la operación «text» correspondiente a la responsabilidad de UpdateSiteLayerMergePreview. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String text(String value) { return value == null ? "" : value.trim(); }
}
