package com.example.editor.registry.federation;

/** Explainable decision for one federated catalog collision. */
public record HydraCatalogSyncConflict(String key, String localEntryId, String incomingEntryId,
        HydraCatalogConflictKind kind, String resolution, String message) { }
