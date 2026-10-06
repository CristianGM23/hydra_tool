package com.example.editor.portability.transaction;

import java.util.List;
import java.util.Map;

/** Result of an applied or preview-only portable layer transaction. */
public record CapabilityLayerTransactionResult(CapabilityLayerTransactionPreview preview,
        String transactionId, boolean persisted, boolean rollbackAvailable,
        Map<String, String> appliedEtags, List<String> diagnostics) {
    public CapabilityLayerTransactionResult {
        appliedEtags = appliedEtags == null ? Map.of() : Map.copyOf(appliedEtags);
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }
}
