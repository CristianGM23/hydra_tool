package com.example.editor.audit;

import java.time.Instant;
import java.util.List;

/** Petición de borrado manual. El controller impone siempre actor y workspace actuales. */
public record AuditDeletionRequest(String mode, List<String> module, List<String> action, String text, Instant from, Instant to) {
    public AuditDeletionRequest {
        module = module == null ? List.of() : List.copyOf(module);
        action = action == null ? List.of() : List.copyOf(action);
        text = text == null ? "" : text.trim();
    }
}
