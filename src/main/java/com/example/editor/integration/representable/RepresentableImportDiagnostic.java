package com.example.editor.integration.representable;

/** Explicit explanation for a fully, partially or non-representable external change. */
public record RepresentableImportDiagnostic(String externalId, Representability representability,
        String code, String message, String suggestion) {
    public RepresentableImportDiagnostic {
        representability = representability == null ? Representability.NONE : representability;
        code = code == null ? "REPRESENTABILITY_UNKNOWN" : code.trim();
        message = message == null ? "" : message.trim();
        suggestion = suggestion == null ? "" : suggestion.trim();
    }
}
