package com.example.editor.audit;

import java.time.Instant;
import java.util.Set;

/** Criterio neutral de borrado; nunca elimina datos de otro workspace/actor. */
public record AuditDeletionCriteria(String workspaceId, Set<String> actorIds, AuditDeletionMode mode, Set<String> moduleIds,
        Set<String> actions, String text, Instant from, Instant to) {
    public AuditDeletionCriteria {
        workspaceId = workspaceId == null || workspaceId.isBlank() ? "local" : workspaceId.trim();
        actorIds = actorIds == null ? Set.of() : Set.copyOf(actorIds);
        mode = mode == null ? AuditDeletionMode.FILTERED : mode;
        moduleIds = moduleIds == null ? Set.of() : Set.copyOf(moduleIds);
        actions = actions == null ? Set.of() : Set.copyOf(actions);
        text = text == null ? "" : text.trim();
        if(from != null && to != null && from.isAfter(to)) throw new IllegalArgumentException("from must be before to");
        if(mode == AuditDeletionMode.FILTERED && moduleIds.isEmpty() && actions.isEmpty() && text.isBlank() && from == null && to == null) {
            throw new IllegalArgumentException("FILTERED deletion requires at least one filter");
        }
    }
}
