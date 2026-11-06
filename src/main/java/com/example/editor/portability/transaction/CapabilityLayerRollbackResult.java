package com.example.editor.portability.transaction;

import java.util.List;
import java.util.Map;

/** Result of restoring selected layers from a transaction journal; {@code decisions} are the merge decisions recorded when it was applied. */
public record CapabilityLayerRollbackResult(String transactionId, List<String> restoredDocuments,
        List<String> skippedDocuments, boolean journalClosed, List<String> diagnostics, Map<String, String> decisions) {
    /** Descripción: Inicializa una instancia de CapabilityLayerRollbackResult con sus dependencias y datos de entrada. Entradas: transactionId: identificador que delimita el recurso objetivo; restoredDocuments: dato de entrada necesario para la operación; skippedDocuments: dato de entrada necesario para la operación; journalClosed: dato de entrada necesario para la operación; diagnostics: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public CapabilityLayerRollbackResult(String transactionId, List<String> restoredDocuments,
            List<String> skippedDocuments, boolean journalClosed, List<String> diagnostics) {
        this(transactionId, restoredDocuments, skippedDocuments, journalClosed, diagnostics, Map.of());
    }
    public CapabilityLayerRollbackResult {
        restoredDocuments = restoredDocuments == null ? List.of() : List.copyOf(restoredDocuments);
        skippedDocuments = skippedDocuments == null ? List.of() : List.copyOf(skippedDocuments);
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
        decisions = decisions == null ? Map.of() : Map.copyOf(decisions);
    }
}
