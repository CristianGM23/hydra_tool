package com.example.editor.registry.federation;

import com.example.editor.registry.HydraRegistryIndex;

/** Manual, offline synchronization request. No remote URL is ever dereferenced. */
public record HydraCatalogSyncRequest(String sourceId, HydraRegistryIndex index, boolean apply,
        boolean overwriteTrustedConflicts) { }
