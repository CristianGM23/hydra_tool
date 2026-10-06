package com.example.editor.integration.roundtrip;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import com.example.editor.integration.IntegrationTechnology;

/** Manifiesto determinista para regeneración selectiva y detección de conflictos. */
public record RoundTripManifest(String schemaVersion, String profileId, IntegrationTechnology technology,
        String generatorVersion, Instant createdAt, List<RoundTripElementMapping> mappings,
        Map<String, String> annotations) {
    public RoundTripManifest {
        schemaVersion = schemaVersion == null || schemaVersion.isBlank() ? "1.0" : schemaVersion;
        if(profileId == null || profileId.isBlank()){throw new IllegalArgumentException("profileId is required");}
        if(technology == null){throw new IllegalArgumentException("technology is required");}
        profileId = profileId.trim(); generatorVersion = generatorVersion == null ? "" : generatorVersion.trim();
        createdAt = createdAt == null ? Instant.EPOCH : createdAt;
        mappings = mappings == null ? List.of() : List.copyOf(mappings);
        annotations = annotations == null ? Map.of() : Map.copyOf(annotations);
    }
}
