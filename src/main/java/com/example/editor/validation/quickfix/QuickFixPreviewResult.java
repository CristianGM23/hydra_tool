package com.example.editor.validation.quickfix;

import java.util.List;
import com.example.editor.validation.model.CanonicalModelDocument;

/** Previsualización que no modifica la sesión EMF abierta. */
public record QuickFixPreviewResult(String diagnosticId, String actionId, CanonicalModelDocument previewDocument,
        CanonicalModelChangeSummary changes, List<String> messages) {
    public QuickFixPreviewResult {
        diagnosticId = diagnosticId == null ? "" : diagnosticId;
        actionId = actionId == null ? "" : actionId;
        if(previewDocument == null) throw new IllegalArgumentException("previewDocument must not be null");
        if(changes == null) throw new IllegalArgumentException("changes must not be null");
        messages = messages == null ? List.of() : List.copyOf(messages);
    }
}
