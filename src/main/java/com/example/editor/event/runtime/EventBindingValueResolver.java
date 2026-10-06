package com.example.editor.event.runtime;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.example.editor.event.CanonicalEvent;

/** Resolución segura de rutas y variables para bindings sin depender de JSONPath. */
final class EventBindingValueResolver {
    /** Descripción: Inicializa una instancia de EventBindingValueResolver con sus dependencias y datos de entrada. Entradas: no requiere parámetros de entrada. Salida: deja la instancia inicializada y lista para su uso. */ private EventBindingValueResolver() { }

    /** Descripción: Obtiene la información correspondiente a «read» dentro de EventBindingValueResolver. Entradas: event: dato de entrada necesario para la operación; path: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ static Object read(CanonicalEvent event, String path) {
        if(event == null || path == null || path.isBlank()) return null;
        String normalized = path.startsWith("$.") ? path.substring(2) : path;
        String[] parts = normalized.split("\\.");
        Object current;
        int index;
        if(parts.length == 0) return null;
        current = switch(parts[0]) {
            case "event" -> event;
            case "payload" -> event.payload();
            case "metadata" -> event.metadata();
            case "subject" -> event.subject();
            case "actor" -> event.actor();
            case "context" -> event.context();
            case "source" -> event.source();
            case "id" -> event.id();
            case "eventTypeId" -> event.eventTypeId();
            case "correlationId" -> event.correlationId();
            case "causationId" -> event.causationId();
            default -> event.payload();
        };
        index = switch(parts[0]) {
            case "event", "payload", "metadata", "subject", "actor", "context", "source" -> 1;
            case "id", "eventTypeId", "correlationId", "causationId" -> parts.length;
            default -> 0;
        };
        for(int i = index; i < parts.length; i++) current = child(current, parts[i]);
        return current;
    }

    /** Descripción: Ejecuta la operación «variables» correspondiente a la responsabilidad de EventBindingValueResolver. Entradas: event: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ static Map<String, Object> variables(CanonicalEvent event) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("event", event);
        values.put("payload", event.payload());
        values.put("metadata", event.metadata());
        values.put("subject", event.subject());
        values.put("actor", event.actor());
        values.put("context", event.context());
        values.put("source", event.source());
        values.put("eventId", event.id());
        values.put("eventTypeId", event.eventTypeId());
        values.put("correlationId", event.correlationId());
        values.put("causationId", event.causationId());
        flatten("payload", event.payload(), values, 0);
        flatten("metadata", event.metadata(), values, 0);
        return Map.copyOf(values);
    }

    /** Descripción: Ejecuta la operación «flatten» correspondiente a la responsabilidad de EventBindingValueResolver. Entradas: prefix: dato de entrada necesario para la operación; value: valor de entrada que debe procesarse; output: dato de entrada necesario para la operación; depth: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private static void flatten(String prefix, Object value, Map<String, Object> output, int depth) {
        if(depth > 8 || !(value instanceof Map<?, ?> map)) return;
        for(var entry : map.entrySet()) {
            String key = prefix + "." + entry.getKey();
            output.put(key, entry.getValue());
            flatten(key, entry.getValue(), output, depth + 1);
        }
    }

    /** Descripción: Ejecuta la operación «child» correspondiente a la responsabilidad de EventBindingValueResolver. Entradas: current: dato de entrada necesario para la operación; key: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private static Object child(Object current, String key) {
        if(current == null) return null;
        if(current instanceof Map<?, ?> map) return map.get(key);
        if(current instanceof List<?> list) {
            try { return list.get(Integer.parseInt(key)); }
            catch(NumberFormatException | IndexOutOfBoundsException exception) { return null; }
        }
        if(!key.matches("[A-Za-z][A-Za-z0-9_]*")) return null;
        try {
            Method method = current.getClass().getMethod(key);
            if(method.getParameterCount() != 0) return null;
            return method.invoke(current);
        } catch(ReflectiveOperationException | SecurityException exception) {
            return null;
        }
    }
}
