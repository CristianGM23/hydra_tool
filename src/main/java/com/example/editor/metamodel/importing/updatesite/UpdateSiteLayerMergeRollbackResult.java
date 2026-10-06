package com.example.editor.metamodel.importing.updatesite;

import java.util.List;

/** Resultado seguro del rollback de una transacción de fusión. */
public record UpdateSiteLayerMergeRollbackResult(boolean restored, String editorEtag,
        List<String> diagnostics) {
    public UpdateSiteLayerMergeRollbackResult {
        editorEtag = editorEtag == null ? "" : editorEtag.trim();
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }
}
