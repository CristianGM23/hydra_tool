package com.example.editor.audit;

import java.util.Map;

/** Payload neutral de actividades que solo existen en el cliente (navegación/vistas). */
public record AuditClientActivityRequest(String activityType, String moduleId, String action, String importance,
        String resourceType, String resourceId, String displayName, Object before, Object after, Map<String, Object> metadata, String dedupeKey) { }
