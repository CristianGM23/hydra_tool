package com.example.editor.metamodel.export.project;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Evidencia reproducible de todas las entradas que originaron un workspace tecnológico. */
public record MetamodelProjectGenerationSnapshot(String schemaVersion, String metamodelId, String metamodelFingerprint,
        String projectExport, String selectionMode, String editorId, Map<String, String> settings,
        List<MetamodelProjectGenerationArtifactSnapshot> artifacts, String fingerprint, Instant capturedAt) {
    public MetamodelProjectGenerationSnapshot {
        settings = settings == null ? Map.of() : Map.copyOf(settings);
        artifacts = artifacts == null ? List.of() : List.copyOf(artifacts);
        capturedAt = capturedAt == null ? Instant.EPOCH : capturedAt;
    }
}
