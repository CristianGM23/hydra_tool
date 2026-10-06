package com.example.editor.metamodel.importing.updatesite;

import java.util.List;

/** Resultado persistido con datos suficientes para un rollback explícito. */
public record UpdateSiteLayerMaterializationResult(UpdateSiteLayerMaterializationPreview preview,
        String metamodelId, String metamodelEtag, String editorId, String editorEtag,
        boolean persisted, boolean rollbackAvailable, List<String> diagnostics) {
    public UpdateSiteLayerMaterializationResult {
        metamodelId = text(metamodelId);
        metamodelEtag = text(metamodelEtag);
        editorId = text(editorId);
        editorEtag = text(editorEtag);
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }
    /** Descripción: Ejecuta la operación «text» correspondiente a la responsabilidad de UpdateSiteLayerMaterializationResult. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String text(String value) { return value == null ? "" : value.trim(); }
}
