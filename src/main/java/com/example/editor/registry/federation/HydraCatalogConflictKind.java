package com.example.editor.registry.federation;

/** Conflict detected while comparing a portable AW index with the local registry. */
public enum HydraCatalogConflictKind { VERSION_COLLISION, ENTRY_ID_COLLISION, UNTRUSTED_SOURCE, INDEX_PIN_MISMATCH }
