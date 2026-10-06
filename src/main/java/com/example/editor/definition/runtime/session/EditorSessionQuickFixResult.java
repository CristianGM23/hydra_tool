package com.example.editor.definition.runtime.session;

import java.util.List;

/** Resultado transaccional de aplicar un quick fix dentro del runtime neutral. */
public record EditorSessionQuickFixResult(EditorSessionView view, String diagnosticId, String actionId,
        boolean diagnosticResolved, List<String> messages) {
    public EditorSessionQuickFixResult {
        diagnosticId = diagnosticId == null ? "" : diagnosticId;
        actionId = actionId == null ? "" : actionId;
        messages = messages == null ? List.of() : List.copyOf(messages);
    }
}
