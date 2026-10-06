package com.example.editor.event.flow.runtime;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.example.editor.event.CanonicalEvent;
import com.example.editor.event.flow.FlowNode;
import com.example.editor.event.flow.FlowNodeKind;
import com.example.editor.event.flow.FlowTransition;
import com.example.editor.event.flow.FlowTransitionKind;

/** Operaciones puras compartidas por la orquestación y los ejecutores EventFlow. */
final class EventFlowRuntimeSupport {
    /** Descripción: Inicializa una instancia de EventFlowRuntimeSupport con sus dependencias y datos de entrada. Entradas: no requiere parámetros de entrada. Salida: deja la instancia inicializada y lista para su uso. */ private EventFlowRuntimeSupport() { }

    /** Descripción: Ejecuta la operación «mapEvent» correspondiente a la responsabilidad de EventFlowRuntimeSupport. Entradas: event: dato de entrada necesario para la operación; configuration: configuración que gobierna el comportamiento. Salida: devuelve el resultado definido por el contrato del método. */ static CanonicalEvent mapEvent(CanonicalEvent event, Map<String, Object> configuration) {
        Map<String, Object> payload = new LinkedHashMap<>(event.payload());
        Object rawMappings = configuration.get("mappings");
        if(rawMappings instanceof Map<?, ?> mappings) {
            for(Map.Entry<?, ?> entry : mappings.entrySet()) {
                String target = String.valueOf(entry.getKey());
                Object specification = entry.getValue();
                Object value;
                if(specification instanceof String path) value = EventValueResolver.read(event, path);
                else if(specification instanceof Map<?, ?> map && map.containsKey("sourcePath")) value = EventValueResolver.read(event, String.valueOf(map.get("sourcePath")));
                else if(specification instanceof Map<?, ?> map && map.containsKey("fixedValue")) value = map.get("fixedValue");
                else value = specification;
                putPath(payload, target, value);
            }
        }
        return withPayload(event, payload);
    }

    /** Descripción: Ejecuta la operación «putPath» correspondiente a la responsabilidad de EventFlowRuntimeSupport. Entradas: payload: valor de entrada que debe procesarse; path: ruta o ubicación utilizada por la operación; value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ static void putPath(Map<String, Object> payload, String path, Object value) {
        String normalized = path.startsWith("payload.") ? path.substring("payload.".length()) : path;
        String[] parts = normalized.split("\\.");
        Map<String, Object> current = payload;
        for(int index = 0; index < parts.length - 1; index++) {
            Object existing = current.get(parts[index]);
            if(existing instanceof Map<?, ?> map) {
                Map<String, Object> nested = new LinkedHashMap<>();
                map.forEach((key, nestedValue) -> nested.put(String.valueOf(key), nestedValue));
                current.put(parts[index], nested);
                current = nested;
            } else {
                Map<String, Object> nested = new LinkedHashMap<>();
                current.put(parts[index], nested);
                current = nested;
            }
        }
        current.put(parts[parts.length - 1], value);
    }

    /** Descripción: Ejecuta la operación «withPayload» correspondiente a la responsabilidad de EventFlowRuntimeSupport. Entradas: source: dato de entrada necesario para la operación; payload: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ static CanonicalEvent withPayload(CanonicalEvent source, Map<String, Object> payload) {
        return new CanonicalEvent(source.schemaVersion(), source.id(), source.eventTypeId(), source.eventTypeVersion(), source.occurredAt(),
                source.source(), source.context(), source.actor(), source.subject(), source.transactionId(), source.correlationId(),
                source.causationId(), payload, source.metadata());
    }

    /** Descripción: Ejecuta la operación «withRuntimePayload» correspondiente a la responsabilidad de EventFlowRuntimeSupport. Entradas: original: dato de entrada necesario para la operación; updates: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ static Map<String, Object> withRuntimePayload(Map<String, Object> original, Map<String, ?> updates) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>(original);
        LinkedHashMap<String, Object> runtime = new LinkedHashMap<>();
        Object current = payload.get("_hydraFlow");
        if(current instanceof Map<?, ?> map) map.forEach((key, value) -> runtime.put(String.valueOf(key), value));
        updates.forEach(runtime::put);
        payload.put("_hydraFlow", runtime);
        return payload;
    }

    /** Descripción: Ejecuta la operación «selectTransitions» correspondiente a la responsabilidad de EventFlowRuntimeSupport. Entradas: node: dato de entrada necesario para la operación; outgoing: dato de entrada necesario para la operación; success: dato de entrada necesario para la operación; timedOut: dato de entrada necesario para la operación; event: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ static List<FlowTransition> selectTransitions(FlowNode node, List<FlowTransition> outgoing, boolean success,
            boolean timedOut, CanonicalEvent event) {
        if(outgoing.isEmpty()) return List.of();
        if(!success) {
            FlowTransitionKind preferred = timedOut ? FlowTransitionKind.TIMEOUT : FlowTransitionKind.ON_ERROR;
            return outgoing.stream().filter(item -> item.kind() == preferred).findFirst()
                    .or(() -> outgoing.stream().filter(item -> item.kind() == FlowTransitionKind.ON_ERROR).findFirst())
                    .map(List::of).orElse(List.of());
        }
        if(node.kind() == FlowNodeKind.DECISION || node.kind() == FlowNodeKind.ROUTE) {
            boolean condition = EventValueResolver.matches(event, node.configuration());
            FlowTransitionKind expected = condition ? FlowTransitionKind.TRUE : FlowTransitionKind.FALSE;
            return outgoing.stream().filter(item -> item.kind() == expected).findFirst().map(List::of).orElse(List.of());
        }
        if(node.kind() == FlowNodeKind.PARALLEL_SPLIT) {
            return outgoing.stream().filter(item -> item.kind() == FlowTransitionKind.NEXT
                    || item.kind() == FlowTransitionKind.ON_SUCCESS).toList();
        }
        return outgoing.stream().filter(item -> item.kind() == FlowTransitionKind.NEXT || item.kind() == FlowTransitionKind.ON_SUCCESS)
                .findFirst().map(List::of).orElseGet(() -> List.of(outgoing.get(0)));
    }

    /** Descripción: Ejecuta la operación «groupingKey» correspondiente a la responsabilidad de EventFlowRuntimeSupport. Entradas: node: dato de entrada necesario para la operación; event: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ static String groupingKey(FlowNode node, CanonicalEvent event) {
        String path = node == null ? "correlationId" : EventValueResolver.text(node.configuration().get("groupByPath"), "correlationId");
        Object value = EventValueResolver.read(event, path);
        if(value == null || String.valueOf(value).isBlank()) value = event.correlationId().isBlank() ? event.id() : event.correlationId();
        return String.valueOf(value);
    }

    /** Descripción: Ejecuta la operación «stringList» correspondiente a la responsabilidad de EventFlowRuntimeSupport. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ static List<String> stringList(Object value) {
        if(value instanceof Collection<?> values) return values.stream().map(String::valueOf).filter(item -> !item.isBlank()).toList();
        if(value instanceof String text && !text.isBlank()) return java.util.Arrays.stream(text.split(",")).map(String::trim).filter(item -> !item.isBlank()).toList();
        return List.of();
    }

    /** Descripción: Ejecuta la operación «difference» correspondiente a la responsabilidad de EventFlowRuntimeSupport. Entradas: required: dato de entrada necesario para la operación; actual: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ static List<String> difference(List<String> required, Set<String> actual) {
        return required.stream().filter(item -> !actual.contains(item)).toList();
    }

    /** Descripción: Ejecuta la operación «depth» correspondiente a la responsabilidad de EventFlowRuntimeSupport. Entradas: event: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ static int depth(CanonicalEvent event) {
        return EventValueResolver.integer(event.metadata().get("hydra.flow.depth"), 0);
    }

    /** Descripción: Ejecuta la operación «message» correspondiente a la responsabilidad de EventFlowRuntimeSupport. Entradas: throwable: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ static String message(Throwable throwable) {
        return throwable.getMessage() == null ? throwable.getClass().getSimpleName() : throwable.getMessage();
    }

    /** Descripción: Ejecuta la operación «trim» correspondiente a la responsabilidad de EventFlowRuntimeSupport. Entradas: map: dato de entrada necesario para la operación; maximum: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ static <K, V> void trim(LinkedHashMap<K, V> map, int maximum) {
        while(map.size() > maximum) map.remove(map.keySet().iterator().next());
    }
}
