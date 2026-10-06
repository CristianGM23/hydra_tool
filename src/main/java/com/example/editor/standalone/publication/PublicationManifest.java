package com.example.editor.standalone.publication;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Reproducible release manifest published per product, version and channel. */
public record PublicationManifest(String schemaVersion, String productId, String version,
        PublicationChannel channel, PublicationSigningMode signingMode, Instant publishedAt,
        List<PublicationArtifact> artifacts, Map<String, String> metadata) {
    public PublicationManifest {
        schemaVersion = schemaVersion == null || schemaVersion.isBlank() ? "1.0" : schemaVersion;
        publishedAt = publishedAt == null ? Instant.EPOCH : publishedAt;
        artifacts = artifacts == null ? List.of() : artifacts.stream()
                .sorted(java.util.Comparator.comparing(PublicationArtifact::platform)
                        .thenComparing(PublicationArtifact::fileName)).toList();
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
