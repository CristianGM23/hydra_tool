package com.example.editor.portability.transaction;

import java.util.List;
import java.util.Map;

/** Selective rollback guarded by the ETags written by the transaction. */
public record CapabilityLayerRollbackRequest(String transactionId, List<String> documentPaths,
        Map<String, String> expectedCurrentEtags) {
    public CapabilityLayerRollbackRequest {
        transactionId = transactionId == null ? "" : transactionId.trim();
        documentPaths = documentPaths == null ? List.of() : List.copyOf(documentPaths);
        expectedCurrentEtags = expectedCurrentEtags == null ? Map.of() : Map.copyOf(expectedCurrentEtags);
    }
}
