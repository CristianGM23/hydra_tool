package com.example.editor.metamodel.importing.updatesite;

import java.time.Instant;

import com.example.editor.definition.EditorDefinitionModel;

/** Journal interno autocontenido que permite revertir una fusión sin borrar otros artefactos. */
record UpdateSiteLayerMergeJournal(String transactionId, Instant createdAt,
        String targetMetamodelId, String targetEditorId, String originalEtag,
        String appliedEtag, EditorDefinitionModel originalEditor) {
    UpdateSiteLayerMergeJournal {
        transactionId = text(transactionId); createdAt = createdAt == null ? Instant.now() : createdAt;
        targetMetamodelId = text(targetMetamodelId); targetEditorId = text(targetEditorId);
        originalEtag = text(originalEtag); appliedEtag = text(appliedEtag);
    }
    /** Descripción: Ejecuta la operación «text» correspondiente a la responsabilidad de UpdateSiteLayerMergeJournal. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String text(String value) { return value == null ? "" : value.trim(); }
}
