package com.example.editor.metamodel.validation;

import java.time.Instant;

/** Estado del gate que protege las exportaciones de proyectos derivados. */
public record MetamodelValidationGateStatus(String metamodelId, boolean validationRequired, boolean validated,
        boolean current, boolean stale, boolean valid, boolean generationReady, String currentFingerprint,
        String validatedFingerprint, Instant validatedAt, MetamodelValidationSummary summary, String message) {
    public MetamodelValidationGateStatus {
        currentFingerprint = currentFingerprint == null ? "" : currentFingerprint;
        validatedFingerprint = validatedFingerprint == null ? "" : validatedFingerprint;
        validatedAt = validatedAt == null ? Instant.EPOCH : validatedAt;
        summary = summary == null ? new MetamodelValidationSummary(0, 0, 0) : summary;
        message = message == null ? "" : message;
    }
}
