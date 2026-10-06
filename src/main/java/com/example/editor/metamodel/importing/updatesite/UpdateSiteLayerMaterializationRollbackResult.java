package com.example.editor.metamodel.importing.updatesite;

import java.util.List;

/** Resultado del rollback de editor y metamodelo creados por la misma operación. */
public record UpdateSiteLayerMaterializationRollbackResult(boolean editorDeleted, boolean metamodelDeleted,
        List<String> diagnostics) {
    public UpdateSiteLayerMaterializationRollbackResult {
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }
}
