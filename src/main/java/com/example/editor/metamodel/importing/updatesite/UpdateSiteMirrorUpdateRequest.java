package com.example.editor.metamodel.importing.updatesite;

/** Compares and optionally promotes an incremental p2 mirror update. */
public record UpdateSiteMirrorUpdateRequest(String baselineMirrorId, String candidateMirrorId,
        UpdateSiteMirrorChannel channel, boolean promote, boolean allowRemovals,
        boolean retainPrevious, boolean validateRollback) {
    public UpdateSiteMirrorUpdateRequest {
        baselineMirrorId = text(baselineMirrorId); candidateMirrorId = text(candidateMirrorId);
        channel = channel == null ? UpdateSiteMirrorChannel.INTERNAL : channel;
    }
    /** Descripción: Ejecuta la operación «text» correspondiente a la responsabilidad de UpdateSiteMirrorUpdateRequest. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String text(String value){return value == null ? "" : value.trim();}
}
