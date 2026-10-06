package com.example.editor.metamodel.validation;

import java.time.Instant;

/** Evidencia persistida que vincula una validación satisfactoria con una versión concreta del metamodelo. */
public record MetamodelValidationReceipt(String schemaVersion, String metamodelId, String fingerprint, Instant validatedAt,
        boolean valid, boolean generationReady, MetamodelValidationSummary summary) {
    public MetamodelValidationReceipt {
        schemaVersion = schemaVersion == null || schemaVersion.isBlank() ? "1.0" : schemaVersion;
        metamodelId = metamodelId == null ? "" : metamodelId;
        fingerprint = fingerprint == null ? "" : fingerprint;
        validatedAt = validatedAt == null ? Instant.EPOCH : validatedAt;
        summary = summary == null ? new MetamodelValidationSummary(0, 0, 0) : summary;
    }
}
