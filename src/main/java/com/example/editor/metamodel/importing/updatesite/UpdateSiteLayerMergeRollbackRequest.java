package com.example.editor.metamodel.importing.updatesite;

/** Solicita restaurar la versión anterior registrada por una transacción de fusión. */
public record UpdateSiteLayerMergeRollbackRequest(String transactionId, String expectedCurrentEtag) {
    public UpdateSiteLayerMergeRollbackRequest {
        transactionId = transactionId == null ? "" : transactionId.trim();
        expectedCurrentEtag = expectedCurrentEtag == null ? "" : expectedCurrentEtag.trim();
    }
}
