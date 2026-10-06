package com.example.editor.portability.transaction;

import java.time.Instant;
import java.util.List;

record CapabilityLayerTransactionJournal(String transactionId, String targetMetamodelId,
        Instant createdAt, List<CapabilityLayerTransactionJournalItem> items) {
    CapabilityLayerTransactionJournal {
        createdAt = createdAt == null ? Instant.now() : createdAt;
        items = items == null ? List.of() : List.copyOf(items);
    }
}
