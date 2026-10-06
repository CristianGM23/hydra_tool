package com.example.editor.metamodel.importing.updatesite;

import java.util.List;

/** Resultado de una previsualización o una fusión persistida. */
public record UpdateSiteLayerMergeResult(UpdateSiteLayerMergePreview preview, boolean persisted,
        String editorEtag, boolean rollbackAvailable, List<String> diagnostics) {
    public UpdateSiteLayerMergeResult {
        editorEtag = editorEtag == null ? "" : editorEtag.trim();
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }
}
