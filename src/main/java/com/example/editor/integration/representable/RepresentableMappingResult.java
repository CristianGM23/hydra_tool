package com.example.editor.integration.representable;

import java.util.List;

/** Mapper result for a single external change candidate. */
public record RepresentableMappingResult(NeutralChangeOperation operation,
        List<RepresentableImportDiagnostic> diagnostics) {
    public RepresentableMappingResult {
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }
}
