package com.example.editor.audit;

import java.util.Map;

/** Actor neutral. El proveedor local se sustituirá por autenticación real en BG sin cambiar el log. */
public record AuditActor(String actorId, String displayName, String sessionId, Map<String, String> attributes) {
    public AuditActor {
        actorId = require(actorId, "actorId");
        displayName = displayName == null || displayName.isBlank() ? actorId : displayName.trim();
        sessionId = sessionId == null ? "" : sessionId.trim();
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }
    /** Descripción: Valida las condiciones necesarias para «require» y rechaza entradas no válidas. Entradas: value: valor de entrada que debe procesarse; field: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String require(String value, String field) {
        if(value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }
}
