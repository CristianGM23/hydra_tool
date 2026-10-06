package com.example.editor.metamodel.projection;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Registra qué contenido canónico originó cada proyección y su último estado.
 */
public record ProjectionManifest(String schemaVersion, String metamodelId, String canonicalEtag, Instant updatedAt, Map<String, ProjectionResult> projections) {

    public static final String CURRENT_SCHEMA_VERSION = "1.0";

    public ProjectionManifest {
        schemaVersion = schemaVersion == null || schemaVersion.isBlank() ? CURRENT_SCHEMA_VERSION : schemaVersion;

        if (!CURRENT_SCHEMA_VERSION.equals(schemaVersion)) {
            throw new IllegalArgumentException("Unsupported projection manifest schema version: " + schemaVersion);
        }

        metamodelId = metamodelId == null ? "" : metamodelId;
        canonicalEtag = canonicalEtag == null ? "" : canonicalEtag;
        updatedAt = updatedAt == null ? Instant.now() : updatedAt;
        projections = projections == null || projections.isEmpty() ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(projections));
    }
}
