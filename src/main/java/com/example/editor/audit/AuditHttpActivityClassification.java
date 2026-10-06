package com.example.editor.audit;

/** Resultado neutral de clasificar una petición HTTP como acción funcional. */
public record AuditHttpActivityClassification(String activityType, String moduleId, String action, AuditImportance importance,
        String resourceType, String resourceId, String displayName, boolean captureAlongsideMutation) {
    public AuditHttpActivityClassification {
        activityType = required(activityType, "activityType");
        moduleId = required(moduleId, "moduleId");
        action = required(action, "action");
        importance = importance == null ? AuditImportance.INFO : importance;
        resourceType = required(resourceType, "resourceType");
        resourceId = required(resourceId, "resourceId");
        displayName = displayName == null || displayName.isBlank() ? resourceId : displayName.trim();
    }
    /** Descripción: Valida las condiciones necesarias para «required» y rechaza entradas no válidas. Entradas: value: valor de entrada que debe procesarse; field: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String required(String value, String field) {
        if(value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }
}
