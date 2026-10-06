package com.example.editor.audit;

import java.util.Locale;

import org.springframework.stereotype.Component;

@Component
public class ConfiguredAuditRedactionPolicy implements AuditRedactionPolicy {
    private final AuditLogProperties properties;
    /** Descripción: Inicializa una instancia de ConfiguredAuditRedactionPolicy con sus dependencias y datos de entrada. Entradas: properties: configuración que gobierna el comportamiento. Salida: deja la instancia inicializada y lista para su uso. */ public ConfiguredAuditRedactionPolicy(AuditLogProperties properties) { this.properties = properties; }
    @Override
    /** Descripción: Ejecuta la operación «sensitive» correspondiente a la responsabilidad de ConfiguredAuditRedactionPolicy. Entradas: path: ruta o ubicación utilizada por la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean sensitive(String path) {
        String normalized = path == null ? "" : path.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "");
        return properties.getSensitiveKeys().stream().filter(item -> item != null && !item.isBlank())
                .map(item -> item.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", ""))
                .anyMatch(key -> !key.isBlank() && normalized.contains(key));
    }
}
