package com.example.editor.audit;

import java.util.Locale;

import org.springframework.stereotype.Component;

/** Política central para no convertir Activity Log en un volcado de estados técnicos/efímeros. */
@Component
public class AuditCapturePolicy {
    private final AuditLogProperties properties;
    /** Descripción: Inicializa una instancia de AuditCapturePolicy con sus dependencias y datos de entrada. Entradas: properties: configuración que gobierna el comportamiento. Salida: deja la instancia inicializada y lista para su uso. */ public AuditCapturePolicy(AuditLogProperties properties) { this.properties = properties; }

    /** Descripción: Ejecuta la operación «capturesNamespace» correspondiente a la responsabilidad de AuditCapturePolicy. Entradas: namespace: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean capturesNamespace(String namespace) {
        if(!properties.isEnabled() || namespace == null || namespace.isBlank()) return false;
        String normalized = namespace.trim().toLowerCase(Locale.ROOT);
        return properties.getIgnoredNamespaces().stream()
                .filter(item -> item != null && !item.isBlank())
                .map(item -> item.trim().toLowerCase(Locale.ROOT))
                .noneMatch(pattern -> matches(pattern, normalized));
    }

    /** Descripción: Evalúa la condición «matches» sin modificar el estado externo. Entradas: pattern: dato de entrada necesario para la operación; value: valor de entrada que debe procesarse. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean matches(String pattern, String value) {
        if(pattern.endsWith(".*")) return value.startsWith(pattern.substring(0, pattern.length() - 1));
        return value.equals(pattern);
    }
}
