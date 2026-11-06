package com.example.editor.metamodel.importing.updatesite;

import java.time.Instant;
import java.util.Map;

import com.example.editor.definition.EditorDefinitionModel;

/**
 * Journal interno autocontenido que permite revertir una fusión sin borrar otros artefactos.
 * {@code decisions} conserva las decisiones de fusión que lo originaron (clave del elemento → decisión).
 */
record UpdateSiteLayerMergeJournal(String transactionId, Instant createdAt,
        String targetMetamodelId, String targetEditorId, String originalEtag,
        String appliedEtag, EditorDefinitionModel originalEditor, Map<String, String> decisions) {
    /** Descripción: Inicializa una instancia de UpdateSiteLayerMergeJournal con sus dependencias y datos de entrada. Entradas: transactionId: identificador que delimita el recurso objetivo; createdAt: dato de entrada necesario para la operación; targetMetamodelId: identificador que delimita el recurso objetivo; targetEditorId: identificador que delimita el recurso objetivo; originalEtag: dato de entrada necesario para la operación; appliedEtag: dato de entrada necesario para la operación; originalEditor: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ UpdateSiteLayerMergeJournal(String transactionId, Instant createdAt, String targetMetamodelId, String targetEditorId,
            String originalEtag, String appliedEtag, EditorDefinitionModel originalEditor) {
        this(transactionId, createdAt, targetMetamodelId, targetEditorId, originalEtag, appliedEtag, originalEditor, Map.of());
    }
    UpdateSiteLayerMergeJournal {
        transactionId = text(transactionId); createdAt = createdAt == null ? Instant.now() : createdAt;
        targetMetamodelId = text(targetMetamodelId); targetEditorId = text(targetEditorId);
        originalEtag = text(originalEtag); appliedEtag = text(appliedEtag);
        decisions = decisions == null ? Map.of() : Map.copyOf(decisions);
    }
    /** Descripción: Ejecuta la operación «text» correspondiente a la responsabilidad de UpdateSiteLayerMergeJournal. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String text(String value) { return value == null ? "" : value.trim(); }
}
