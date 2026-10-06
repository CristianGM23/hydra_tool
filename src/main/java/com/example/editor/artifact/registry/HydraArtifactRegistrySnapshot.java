package com.example.editor.artifact.registry;

import java.time.Instant;
import java.util.List;

/** Deterministic persisted snapshot of all known neutral artifacts. */
public record HydraArtifactRegistrySnapshot(String schemaVersion, Instant generatedAt,
        List<HydraArtifactDescriptor> artifacts) {
    public HydraArtifactRegistrySnapshot {
        schemaVersion = schemaVersion == null || schemaVersion.isBlank() ? "1.0" : schemaVersion.trim();
        generatedAt = generatedAt == null ? Instant.EPOCH : generatedAt;
        artifacts = artifacts == null ? List.of() : artifacts.stream()
                .sorted(java.util.Comparator.comparing(HydraArtifactDescriptor::id)).toList();
    }
}
