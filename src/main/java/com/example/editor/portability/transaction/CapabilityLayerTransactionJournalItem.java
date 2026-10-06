package com.example.editor.portability.transaction;

record CapabilityLayerTransactionJournalItem(String documentPath, String targetPath,
        boolean existed, String originalBase64, String appliedEtag, boolean rolledBack) { }
