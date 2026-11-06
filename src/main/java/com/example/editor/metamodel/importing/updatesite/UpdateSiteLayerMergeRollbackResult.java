package com.example.editor.metamodel.importing.updatesite;

import java.util.List;
import java.util.Map;

/** Resultado seguro del rollback de una transacción de fusión; {@code decisions} son las decisiones registradas al aplicarla. */
public record UpdateSiteLayerMergeRollbackResult(boolean restored, String editorEtag,
        List<String> diagnostics, Map<String, String> decisions) {
    /** Descripción: Inicializa una instancia de UpdateSiteLayerMergeRollbackResult con sus dependencias y datos de entrada. Entradas: restored: dato de entrada necesario para la operación; editorEtag: dato de entrada necesario para la operación; diagnostics: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public UpdateSiteLayerMergeRollbackResult(boolean restored, String editorEtag, List<String> diagnostics) {
        this(restored, editorEtag, diagnostics, Map.of());
    }
    public UpdateSiteLayerMergeRollbackResult {
        editorEtag = editorEtag == null ? "" : editorEtag.trim();
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
        decisions = decisions == null ? Map.of() : Map.copyOf(decisions);
    }
}
