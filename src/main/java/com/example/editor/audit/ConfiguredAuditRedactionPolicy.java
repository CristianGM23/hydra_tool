package com.example.editor.audit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Component;

@Component
public class ConfiguredAuditRedactionPolicy implements AuditRedactionPolicy {
    private final AuditLogProperties properties;
    /** Claves normalizadas de la última lista configurada vista; se recalculan si esa lista cambia, porque es mutable. */
    private volatile NormalizedKeys keys = new NormalizedKeys(List.of(), List.of());
    /** Descripción: Inicializa una instancia de ConfiguredAuditRedactionPolicy con sus dependencias y datos de entrada. Entradas: properties: configuración que gobierna el comportamiento. Salida: deja la instancia inicializada y lista para su uso. */ public ConfiguredAuditRedactionPolicy(AuditLogProperties properties) { this.properties = properties; }
    @Override
    /** Descripción: Ejecuta la operación «sensitive» correspondiente a la responsabilidad de ConfiguredAuditRedactionPolicy. Entradas: path: ruta o ubicación utilizada por la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean sensitive(String path) {
        String normalized = normalize(path == null ? "" : path);
        for(String key : keys().normalized()) if(normalized.contains(key)) return true;
        return false;
    }

    /** Descripción: Devuelve las claves sensibles normalizadas y no vacías, reutilizando el cálculo mientras la lista configurada no cambie. Entradas: no requiere parámetros de entrada. Salida: lista inmutable de claves normalizadas. */ private NormalizedKeys keys() {
        List<String> configured = properties.getSensitiveKeys();
        NormalizedKeys current = keys;
        if(current.source().equals(configured)) return current;
        List<String> source = Collections.unmodifiableList(new ArrayList<>(configured));
        List<String> normalized = new ArrayList<>();
        for(String item : source) {
            if(item == null || item.isBlank()) continue;
            String key = normalize(item);
            if(!key.isEmpty()) normalized.add(key);
        }
        current = new NormalizedKeys(source, List.copyOf(normalized));
        keys = current;
        return current;
    }

    /** Descripción: Equivale a {@code value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "")} sin compilar una expresión regular en cada llamada. Entradas: value: texto no nulo. Salida: texto en minúsculas con solo a-z, 0-9 y _. */ static String normalize(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        StringBuilder result = null;
        for(int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            boolean kept = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_';
            if(kept) { if(result != null) result.append(c); }
            else if(result == null) result = new StringBuilder(lower.length()).append(lower, 0, i);
        }
        return result == null ? lower : result.toString();
    }

    private record NormalizedKeys(List<String> source, List<String> normalized) { }
}
