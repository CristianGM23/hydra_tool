package com.example.editor.integration.representable;

import java.time.Instant;
import java.util.List;

/** Deterministic preview separating neutral operations from explicit unsupported diagnostics. */
public record RepresentableImportPreview(String previewId, String technology, String metamodelId,
        Instant generatedAt, List<NeutralChangeOperation> operations,
        List<RepresentableImportDiagnostic> diagnostics, boolean applicable) {
    public RepresentableImportPreview {
        generatedAt = generatedAt == null ? Instant.EPOCH : generatedAt;
        operations = operations == null ? List.of() : List.copyOf(operations);
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }
}
