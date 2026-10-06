package com.example.editor.audit;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectWriter;
import tools.jackson.databind.SerializationFeature;

/** Captura estructuras inmutables antes de abandonar el hilo de la operación de negocio. */
@Component
public class AuditSnapshotService {
    private final ObjectMapper mapper;
    private final ObjectWriter writer;
    private final AuditLogProperties properties;
    private final AuditRedactionRegistry redaction;

    /** Descripción: Inicializa una instancia de AuditSnapshotService con sus dependencias y datos de entrada. Entradas: mapper: componente de conversión utilizado durante el procesamiento; properties: configuración que gobierna el comportamiento; redaction: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public AuditSnapshotService(ObjectMapper mapper, AuditLogProperties properties, AuditRedactionRegistry redaction) {
        this.mapper = mapper;
        this.writer = mapper.writer(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
        this.properties = properties;
        this.redaction = redaction;
    }

    /** Descripción: Ejecuta la operación «snapshot» correspondiente a la responsabilidad de Budget. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ public Object snapshot(Object value) {
        if(value == null) return null;
        if(isSimple(value)) return trim(value);
        try {
            byte[] serialized = writer.writeValueAsBytes(value);
            if(serialized.length > properties.getMaximumSnapshotBytes()) return "[snapshot omitted: " + serialized.length + " bytes]";
            Object generic = mapper.readValue(serialized, Object.class);
            return sanitize(generic, "$", 0, new Budget(properties.getMaximumSnapshotNodes()));
        } catch(JacksonException exception) {
            return trim(String.valueOf(value));
        }
    }


    /** Descripción: Ejecuta la operación «actor» correspondiente a la responsabilidad de Budget. Entradas: actor: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public AuditActor actor(AuditActor actor) {
        if(actor == null) return new AuditActor("local-user", "Local user", "", Map.of());
        Map<String, String> attributes = new LinkedHashMap<>();
        for(Map.Entry<String, String> entry : actor.attributes().entrySet()) {
            String value = redaction.sensitive("actor." + entry.getKey()) ? AuditRedactionRegistry.REDACTED : String.valueOf(trim(entry.getValue()));
            attributes.put(entry.getKey(), value);
        }
        return new AuditActor(actor.actorId(), actor.displayName(), actor.sessionId(), Collections.unmodifiableMap(attributes));
    }

    /** Descripción: Ejecuta la operación «metadata» correspondiente a la responsabilidad de Budget. Entradas: values: colección de elementos que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ public Map<String, Object> metadata(Map<String, Object> values) {
        if(values == null || values.isEmpty()) return Map.of();
        Map<String, Object> result = new LinkedHashMap<>();
        Budget budget = new Budget(properties.getMaximumSnapshotNodes());
        int limit = Math.min(values.size(), properties.getMaximumMetadataEntries());
        int index = 0;
        for(Map.Entry<String, Object> entry : values.entrySet()) {
            if(index++ >= limit) break;
            String key = entry.getKey();
            String path = "metadata." + key;
            result.put(key, redaction.sensitive(path) ? AuditRedactionRegistry.REDACTED : sanitize(entry.getValue(), path, 0, budget));
        }
        if(values.size() > limit) result.put("_truncatedMetadataEntries", values.size() - limit);
        return Collections.unmodifiableMap(result);
    }

    /** Descripción: Ejecuta la operación «sanitize» correspondiente a la responsabilidad de Budget. Entradas: value: valor de entrada que debe procesarse; path: ruta o ubicación utilizada por la operación; depth: dato de entrada necesario para la operación; budget: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Object sanitize(Object value, String path, int depth, Budget budget) {
        if(value == null) return null;
        if(!budget.take()) return "[snapshot node limit]";
        if(depth > 20) return "[depth limit]";
        if(value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for(Map.Entry<?, ?> entry : map.entrySet()) {
                if(result.size() >= properties.getMaximumMetadataEntries() * 4) { result.put("_truncated", true); break; }
                String key = String.valueOf(entry.getKey());
                String childPath = path + "." + key;
                result.put(key, redaction.sensitive(childPath) ? AuditRedactionRegistry.REDACTED : sanitize(entry.getValue(), childPath, depth + 1, budget));
            }
            return Collections.unmodifiableMap(result);
        }
        if(value instanceof List<?> list) {
            int maximum = Math.min(list.size(), 2000);
            java.util.ArrayList<Object> result = new java.util.ArrayList<>(maximum + 1);
            for(int i = 0; i < maximum; i++) result.add(sanitize(list.get(i), path + "[" + i + "]", depth + 1, budget));
            if(list.size() > maximum) result.add("[" + (list.size() - maximum) + " more items]");
            return Collections.unmodifiableList(result);
        }
        return isSimple(value) ? trim(value) : sanitize(convertUnchecked(value), path, depth + 1, budget);
    }

    /** Descripción: Ejecuta la operación «convertUnchecked» correspondiente a la responsabilidad de Budget. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private Object convertUnchecked(Object value) {
        try { return mapper.readValue(writer.writeValueAsBytes(value), Object.class); }
        catch(Exception ignored) { return trim(String.valueOf(value)); }
    }
    /** Descripción: Ejecuta la operación «trim» correspondiente a la responsabilidad de Budget. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private Object trim(Object value) {
        if(!(value instanceof String text)) return value;
        int max = properties.getMaximumValueLength();
        return text.length() <= max ? text : text.substring(0, max) + "… [truncated]";
    }
    /** Descripción: Evalúa la condición «isSimple» sin modificar el estado externo. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean isSimple(Object value) {
        return value instanceof String || value instanceof Number || value instanceof Boolean || value instanceof Character || value.getClass().isEnum();
    }
    private static final class Budget {
        private int remaining;
        /** Descripción: Inicializa una instancia de Budget con sus dependencias y datos de entrada. Entradas: remaining: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ private Budget(int remaining) { this.remaining = Math.max(1, remaining); }
        /** Descripción: Ejecuta la operación «take» correspondiente a la responsabilidad de Budget. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean take() { if(remaining <= 0) return false; remaining--; return true; }
    }
}
