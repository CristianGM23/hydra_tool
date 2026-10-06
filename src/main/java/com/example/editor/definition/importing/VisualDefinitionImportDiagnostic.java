package com.example.editor.definition.importing;

import java.util.List;

/** Actionable diagnostic emitted while recovering an external visual definition into HYDRA. */
public record VisualDefinitionImportDiagnostic(String severity, String code, String source, String location,
        String element, String message, String explanation, String suggestedAction, List<String> fixIds,
        String confidence) {
    public VisualDefinitionImportDiagnostic {
        severity = severity == null || severity.isBlank() ? "INFO" : severity.trim().toUpperCase();
        code = code == null || code.isBlank() ? "IMPORT" : code.trim();
        source = source == null ? "" : source;
        location = location == null ? "" : location;
        element = element == null ? "" : element;
        message = message == null ? "" : message;
        explanation = explanation == null ? "" : explanation;
        suggestedAction = suggestedAction == null ? "" : suggestedAction;
        fixIds = fixIds == null ? List.of() : List.copyOf(fixIds);
        confidence = confidence == null || confidence.isBlank() ? "UNKNOWN" : confidence.trim().toUpperCase();
    }

}
