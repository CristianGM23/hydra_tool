package com.example.editor.metamodel.validation;

import java.time.Instant;
import java.util.List;

/** Resultado exhaustivo y estable de una validación de metamodelo. */
public record MetamodelValidationAnalysis(String metamodelId, String metamodelName, String fingerprint,
        Instant validatedAt, boolean valid, boolean generationReady, MetamodelValidationSummary summary,
        List<MetamodelValidationDiagnostic> diagnostics) {
    public MetamodelValidationAnalysis {
        metamodelId = metamodelId == null ? "" : metamodelId;
        metamodelName = metamodelName == null ? metamodelId : metamodelName;
        fingerprint = fingerprint == null ? "" : fingerprint;
        validatedAt = validatedAt == null ? Instant.now() : validatedAt;
        summary = summary == null ? new MetamodelValidationSummary(0, 0, 0) : summary;
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }
}
