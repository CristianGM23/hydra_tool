package com.example.editor.audit;

/** Descriptor registrable. Un módulo futuro añade un provider sin editar el core. */
public record AuditActivityDescriptor(String activityType, String moduleId, String action,
        String titleEnglish, String titleSpanish, String moduleEnglish, String moduleSpanish,
        AuditImportance importance) {
    public AuditActivityDescriptor {
        if(activityType == null || activityType.isBlank()) throw new IllegalArgumentException("activityType is required");
        moduleId = moduleId == null || moduleId.isBlank() ? "general" : moduleId.trim();
        action = action == null || action.isBlank() ? AuditActions.UPDATE : action.trim();
        titleEnglish = titleEnglish == null || titleEnglish.isBlank() ? "Activity" : titleEnglish.trim();
        titleSpanish = titleSpanish == null || titleSpanish.isBlank() ? titleEnglish : titleSpanish.trim();
        moduleEnglish = moduleEnglish == null || moduleEnglish.isBlank() ? humanize(moduleId) : moduleEnglish.trim();
        moduleSpanish = moduleSpanish == null || moduleSpanish.isBlank() ? moduleEnglish : moduleSpanish.trim();
        importance = importance == null ? AuditImportance.INFO : importance;
    }
    /** Descripción: Ejecuta la operación «humanize» correspondiente a la responsabilidad de AuditActivityDescriptor. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String humanize(String value) {
        String spaced = value.replace('.', ' ').replace('_', ' ').replace('-', ' ').trim();
        return spaced.isEmpty() ? "General" : Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }
}
