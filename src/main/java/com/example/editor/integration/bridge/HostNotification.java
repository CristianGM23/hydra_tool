package com.example.editor.integration.bridge;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
/** Notificación neutral para paneles, mensajes o progreso gamificado. */
public record HostNotification(String kind, String title, String message, Map<String, String> metadata) {
    public HostNotification {
        kind = normalize(kind); title = normalize(title); message = normalize(message);
        metadata = metadata == null || metadata.isEmpty() ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
    }
    /** Descripción: Ejecuta la operación «normalize» correspondiente a la responsabilidad de HostNotification. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String normalize(String value) { return value == null ? "" : value.trim(); }
}
