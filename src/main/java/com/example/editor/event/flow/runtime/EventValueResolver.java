package com.example.editor.event.flow.runtime;

import java.util.List;
import java.util.Map;
import com.example.editor.event.CanonicalEvent;

/** Resuelve rutas sencillas del evento sin depender de JSONPath. */
final class EventValueResolver {
    /** Descripción: Inicializa una instancia de EventValueResolver con sus dependencias y datos de entrada. Entradas: no requiere parámetros de entrada. Salida: deja la instancia inicializada y lista para su uso. */ private EventValueResolver() { }
    /** Descripción: Obtiene la información correspondiente a «read» dentro de EventValueResolver. Entradas: event: dato de entrada necesario para la operación; path: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ static Object read(CanonicalEvent event, String path) {
        if(path == null || path.isBlank()) return null;
        String normalized = path.startsWith("$.") ? path.substring(2) : path;
        if(normalized.equals("id")) return event.id();
        if(normalized.equals("eventTypeId")) return event.eventTypeId();
        if(normalized.equals("correlationId")) return event.correlationId();
        if(normalized.equals("causationId")) return event.causationId();
        if(normalized.startsWith("payload.")) return nested(event.payload(), normalized.substring("payload.".length()));
        if(normalized.startsWith("metadata.")) return nested(event.metadata(), normalized.substring("metadata.".length()));
        return nested(event.payload(), normalized);
    }
    /** Descripción: Ejecuta la operación «nested» correspondiente a la responsabilidad de EventValueResolver. Entradas: map: dato de entrada necesario para la operación; path: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static Object nested(Map<?, ?> map, String path) {
        Object current = map;
        for(String part : path.split("\\.")) {
            if(current instanceof Map<?, ?> values) current = values.get(part);
            else if(current instanceof List<?> values) {
                try { current = values.get(Integer.parseInt(part)); }
                catch(NumberFormatException | IndexOutOfBoundsException exception) { return null; }
            } else return null;
        }
        return current;
    }
    /** Descripción: Evalúa la condición «matches» sin modificar el estado externo. Entradas: event: dato de entrada necesario para la operación; configuration: configuración que gobierna el comportamiento. Salida: devuelve un indicador que expresa el resultado de la condición. */ static boolean matches(CanonicalEvent event, Map<String, Object> configuration) {
        String path = text(configuration.get("conditionPath"), text(configuration.get("path"), ""));
        if(path.isBlank()) return true;
        Object actual = read(event, path);
        Object expected = configuration.containsKey("expectedValue") ? configuration.get("expectedValue") : configuration.get("equals");
        String operator = text(configuration.get("operator"), "EQUALS").toUpperCase();
        return switch(operator) {
            case "NOT_EQUALS" -> !java.util.Objects.equals(actual, expected);
            case "EXISTS" -> actual != null;
            case "NOT_EXISTS" -> actual == null;
            case "CONTAINS" -> actual != null && String.valueOf(actual).contains(String.valueOf(expected));
            case "GREATER_THAN" -> number(actual) > number(expected);
            case "LESS_THAN" -> number(actual) < number(expected);
            default -> java.util.Objects.equals(normalizeNumber(actual), normalizeNumber(expected));
        };
    }
    /** Descripción: Ejecuta la operación «text» correspondiente a la responsabilidad de EventValueResolver. Entradas: value: valor de entrada que debe procesarse; fallback: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ static String text(Object value, String fallback) { return value == null ? fallback : String.valueOf(value); }
    /** Descripción: Ejecuta la operación «integer» correspondiente a la responsabilidad de EventValueResolver. Entradas: value: valor de entrada que debe procesarse; fallback: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ static int integer(Object value, int fallback) { try { return value == null ? fallback : Integer.parseInt(String.valueOf(value)); } catch(NumberFormatException exception) { return fallback; } }
    /** Descripción: Ejecuta la operación «longValue» correspondiente a la responsabilidad de EventValueResolver. Entradas: value: valor de entrada que debe procesarse; fallback: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ static long longValue(Object value, long fallback) { try { return value == null ? fallback : Long.parseLong(String.valueOf(value)); } catch(NumberFormatException exception) { return fallback; } }
    /** Descripción: Ejecuta la operación «number» correspondiente a la responsabilidad de EventValueResolver. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static double number(Object value) { try { return value == null ? Double.NaN : Double.parseDouble(String.valueOf(value)); } catch(NumberFormatException exception) { return Double.NaN; } }
    /** Descripción: Ejecuta la operación «normalizeNumber» correspondiente a la responsabilidad de EventValueResolver. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static Object normalizeNumber(Object value) {
        if(value instanceof Number number) return number.doubleValue();
        return value;
    }
}
