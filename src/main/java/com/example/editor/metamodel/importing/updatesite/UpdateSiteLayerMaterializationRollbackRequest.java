package com.example.editor.metamodel.importing.updatesite;

/** Identificadores y etags devueltos por la materialización persistida. */
public record UpdateSiteLayerMaterializationRollbackRequest(String metamodelId, String metamodelEtag,
        String editorId, String editorEtag) {
    public UpdateSiteLayerMaterializationRollbackRequest {
        metamodelId = text(metamodelId); metamodelEtag = text(metamodelEtag);
        editorId = text(editorId); editorEtag = text(editorEtag);
    }
    /** Descripción: Ejecuta la operación «text» correspondiente a la responsabilidad de UpdateSiteLayerMaterializationRollbackRequest. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String text(String value) { return value == null ? "" : value.trim(); }
}
