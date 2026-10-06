package com.example.editor.audit;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Contrato persistido AuditEntry 1.0; es deliberadamente independiente del idioma de presentación. */
public record AuditEntry(String schemaVersion, String eventId, Instant timestamp, AuditActor actor, String workspaceId,
        String activityType, String moduleId, String action, AuditImportance importance, AuditResult result,
        AuditResource resource, List<AuditChange> changes, boolean changesTruncated, Map<String, Object> metadata,
        String correlationId, String parentEventId) {
    public static final String SCHEMA_VERSION = "1.0";
    public AuditEntry {
        schemaVersion = schemaVersion == null || schemaVersion.isBlank() ? SCHEMA_VERSION : schemaVersion.trim();
        if(!SCHEMA_VERSION.equals(schemaVersion)) throw new IllegalArgumentException("Unsupported audit schema version: " + schemaVersion);
        timestamp = timestamp == null ? Instant.EPOCH : timestamp;
        if(eventId == null || eventId.isBlank()) throw new IllegalArgumentException("Audit eventId is required");
        eventId = eventId.trim();
        actor = actor == null ? new AuditActor("local-user", "Local user", "", Map.of()) : actor;
        workspaceId = workspaceId == null || workspaceId.isBlank() ? "local" : workspaceId.trim();
        if(activityType == null || activityType.isBlank()) throw new IllegalArgumentException("Audit activityType is required");
        activityType = activityType.trim();
        moduleId = moduleId == null || moduleId.isBlank() ? "general" : moduleId.trim();
        action = action == null || action.isBlank() ? AuditActions.UPDATE : action.trim().toUpperCase(java.util.Locale.ROOT);
        importance = importance == null ? AuditImportance.INFO : importance;
        result = result == null ? AuditResult.SUCCESS : result;
        if(resource == null) throw new IllegalArgumentException("Audit resource is required");
        changes = changes == null ? List.of() : changes.stream().filter(java.util.Objects::nonNull).toList();
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        correlationId = correlationId == null ? "" : correlationId;
        parentEventId = parentEventId == null ? "" : parentEventId;
    }
}
