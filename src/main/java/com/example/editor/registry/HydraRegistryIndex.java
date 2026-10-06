package com.example.editor.registry;

import java.time.Instant;
import java.util.List;

/** Portable deterministic catalog index. */
public record HydraRegistryIndex(String schemaVersion, Instant generatedAt, List<HydraRegistryEntry> entries,
        String indexSha256) {
    public HydraRegistryIndex {
        schemaVersion = schemaVersion == null || schemaVersion.isBlank() ? "1.0" : schemaVersion;
        generatedAt = generatedAt == null ? Instant.now() : generatedAt;
        entries = entries == null ? List.of() : List.copyOf(entries);
    }
}
