package com.example.editor.validation.action;

import java.util.List;
import java.util.Map;
import com.example.editor.validation.model.CanonicalModelDocument;

/** Resultado ejecutable con estado anterior utilizable por deshacer. */
public record ActionExecutionResult(String actionId, CanonicalModelDocument document, CanonicalModelDocument undoDocument, boolean undoAvailable, List<String> messages, Map<String, String> createdElements) {
    public ActionExecutionResult {
        messages = messages == null ? List.of() : List.copyOf(messages);
        createdElements = createdElements == null ? Map.of() : Map.copyOf(createdElements);
    }
}
