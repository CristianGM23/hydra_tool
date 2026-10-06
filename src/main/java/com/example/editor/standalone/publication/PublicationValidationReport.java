package com.example.editor.standalone.publication;

import java.util.List;

/** Pre-publication validation report. */
public record PublicationValidationReport(boolean valid, List<PublicationDiagnostic> diagnostics) {
    public PublicationValidationReport {
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }
}
