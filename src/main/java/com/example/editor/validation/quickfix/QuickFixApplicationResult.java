package com.example.editor.validation.quickfix;

import java.util.List;
import com.example.editor.model.ModelDocumentResponse;
import com.example.editor.validation.orchestration.OrchestratedValidationResult;

/** Resultado transaccional, revalidado y apto para actualizar Problems. */
public record QuickFixApplicationResult(String diagnosticId, String actionId, QuickFixApplicationStatus status,
        boolean diagnosticResolved, ModelDocumentResponse document, CanonicalModelChangeSummary changes,
        OrchestratedValidationResult validation, boolean undoAvailable, boolean redoAvailable, List<String> messages) {
    public QuickFixApplicationResult {
        diagnosticId = diagnosticId == null ? "" : diagnosticId;
        actionId = actionId == null ? "" : actionId;
        if(status == null) throw new IllegalArgumentException("status must not be null");
        if(document == null) throw new IllegalArgumentException("document must not be null");
        if(changes == null) throw new IllegalArgumentException("changes must not be null");
        if(validation == null) throw new IllegalArgumentException("validation must not be null");
        messages = messages == null ? List.of() : List.copyOf(messages);
    }
}
