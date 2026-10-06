package com.example.editor.audit;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record AuditPresentedEntry(String eventId, Instant timestamp, String title, String summary,
        String moduleId, String module, String action, String actionLabel, AuditImportance importance, AuditResult result,
        String resourceType, String resourceId, String resourceName, String actorId, String actorName,
        List<AuditPresentedChange> changes, boolean changesTruncated, String correlationId, Map<String, Object> metadata) { }
