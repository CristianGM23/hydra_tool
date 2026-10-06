package com.example.editor.integration.compiler;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import com.example.editor.integration.IntegrationTechnology;

/** Manifiesto reproducible incluido dentro de cada ZIP tecnológico generado. */
public record TechnologyCompilationArchiveManifest(IntegrationTechnology technology, String schemaVersion,
        Instant createdAt, List<FileEntry> files, Map<String, String> metadata) {
    public TechnologyCompilationArchiveManifest {
        if(technology == null){throw new IllegalArgumentException("technology is required");}
        schemaVersion = schemaVersion == null || schemaVersion.isBlank() ? "1.0" : schemaVersion;
        if(createdAt == null){createdAt = Instant.EPOCH;}
        files = files == null ? List.of() : List.copyOf(files);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
    public record FileEntry(String path, String mediaType, String sha256) { }
}
