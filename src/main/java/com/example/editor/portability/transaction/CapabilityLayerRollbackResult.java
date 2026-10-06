package com.example.editor.portability.transaction;

import java.util.List;

/** Result of restoring selected layers from a transaction journal. */
public record CapabilityLayerRollbackResult(String transactionId, List<String> restoredDocuments,
        List<String> skippedDocuments, boolean journalClosed, List<String> diagnostics) {
    public CapabilityLayerRollbackResult {
        restoredDocuments = restoredDocuments == null ? List.of() : List.copyOf(restoredDocuments);
        skippedDocuments = skippedDocuments == null ? List.of() : List.copyOf(skippedDocuments);
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }
}
