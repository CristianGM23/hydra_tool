package com.example.editor.generated.project;

import java.time.Instant;
import java.util.List;

/** Persisted registry of generated projects available to HYDRA. */
public record GeneratedProjectRegistry(String schemaVersion, Instant modifiedAt,
        List<GeneratedProjectDefinition> projects) {
    public GeneratedProjectRegistry {
        schemaVersion = schemaVersion == null || schemaVersion.isBlank() ? "1.0" : schemaVersion;
        modifiedAt = modifiedAt == null ? Instant.EPOCH : modifiedAt;
        projects = projects == null ? List.of() : projects.stream()
                .sorted(java.util.Comparator.comparing(GeneratedProjectDefinition::id)).toList();
    }
}
