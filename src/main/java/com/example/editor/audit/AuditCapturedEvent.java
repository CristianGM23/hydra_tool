package com.example.editor.audit;

import java.time.Instant;
import java.util.Map;

/** Snapshot inmutable que puede procesarse de manera asíncrona con seguridad. */
public record AuditCapturedEvent(String eventId, Instant timestamp, AuditActor actor, String workspaceId,
        String activityType, String moduleId, String action, AuditImportance importance, AuditResult result,
        AuditResource resource, Object before, Object after, Map<String, Object> metadata,
        String correlationId, String parentEventId) {
    public AuditCapturedEvent {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
