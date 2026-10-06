package com.example.editor.metamodel.importing.updatesite;

/** Solicitud de preview o persistencia de capas recuperadas. */
public record UpdateSiteLayerMaterializationRequest(String targetId, String candidateId,
        String editorId, String editorName, boolean strict, boolean persist,
        UpdateSiteLayerSelection selection) {
    public UpdateSiteLayerMaterializationRequest {
        targetId = text(targetId);
        candidateId = text(candidateId);
        editorId = text(editorId);
        editorName = text(editorName);
        selection = selection == null ? UpdateSiteLayerSelection.all() : selection;
    }
    /** Descripción: Ejecuta la operación «text» correspondiente a la responsabilidad de UpdateSiteLayerMaterializationRequest. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String text(String value) { return value == null ? "" : value.trim(); }
}
