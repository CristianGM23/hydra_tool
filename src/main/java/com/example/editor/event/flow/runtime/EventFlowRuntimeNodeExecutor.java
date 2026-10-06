package com.example.editor.event.flow.runtime;

import static com.example.editor.event.flow.runtime.EventFlowRuntimeSupport.difference;
import static com.example.editor.event.flow.runtime.EventFlowRuntimeSupport.groupingKey;
import static com.example.editor.event.flow.runtime.EventFlowRuntimeSupport.mapEvent;
import static com.example.editor.event.flow.runtime.EventFlowRuntimeSupport.stringList;
import static com.example.editor.event.flow.runtime.EventFlowRuntimeSupport.trim;
import static com.example.editor.event.flow.runtime.EventFlowRuntimeSupport.withPayload;
import static com.example.editor.event.flow.runtime.EventFlowRuntimeSupport.withRuntimePayload;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import com.example.editor.event.CanonicalEvent;
import com.example.editor.event.flow.FlowDefinition;
import com.example.editor.event.flow.FlowNode;

/** Ejecuta la semántica de cada tipo de nodo EventFlow sobre un estado ya bloqueado por el orquestador. */
final class EventFlowRuntimeNodeExecutor {
    private final List<FlowExternalStepExecutor> externalExecutors;

    /** Descripción: Inicializa una instancia de EventFlowRuntimeNodeExecutor con sus dependencias y datos de entrada. Entradas: externalExecutors: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ EventFlowRuntimeNodeExecutor(List<FlowExternalStepExecutor> externalExecutors) {
        this.externalExecutors = externalExecutors == null ? List.of() : List.copyOf(externalExecutors);
    }

    /** Descripción: Procesa la operación «execute» coordinando las responsabilidades de NodeOutcome. Entradas: deployment: dato de entrada necesario para la operación; flow: dato de entrada necesario para la operación; node: dato de entrada necesario para la operación; event: dato de entrada necesario para la operación; variables: dato de entrada necesario para la operación; depth: dato de entrada necesario para la operación; compensableNodes: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ NodeOutcome execute(EventFlowRuntimeDeployment deployment, FlowDefinition flow, FlowNode node, CanonicalEvent event,
            Map<String, Object> variables, int depth, Deque<String> compensableNodes) {
        if(!node.enabled()) return NodeOutcome.pass(event, "Node disabled");
        return switch(node.kind()) {
            case START, MERGE, PARALLEL_SPLIT, DECISION, ROUTE, RETRY, TIMEOUT, FALLBACK, ERROR_HANDLER,
                    COMPENSATION_STEP, SUCCESS_END, ERROR_END -> NodeOutcome.pass(event, "");
            case SOURCE, EVENT_STEP -> node.eventTypeId().isBlank() || node.eventTypeId().equals(event.eventTypeId())
                    ? NodeOutcome.pass(event, "") : NodeOutcome.filtered(event, "Event type does not match");
            case WAIT_FOR_EVENT -> waitForEvent(deployment.state, flow, node, event, variables, compensableNodes);
            case FILTER -> EventValueResolver.matches(event, node.configuration()) ? NodeOutcome.pass(event, "")
                    : NodeOutcome.filtered(event, "Filter condition did not match");
            case MAP -> NodeOutcome.pass(mapEvent(event, node.configuration()), "Payload mapped");
            case DEDUPLICATE -> deduplicate(deployment.state, flow, node, event);
            case EMIT -> emit(deployment.metamodelId, flow, node, event, depth);
            case WINDOW -> window(deployment.state, flow, node, event);
            case JOIN -> join(deployment.state, flow, node, event);
            case AGGREGATE -> aggregate(deployment.state, flow, node, event);
            case SEQUENCE -> sequence(deployment.state, flow, node, event);
            case PATTERN -> pattern(deployment.state, flow, node, event);
            case ABSENCE -> absence(deployment.state, flow, node, event);
            case COMMAND_STEP, ACTION_STEP, OPERATION_STEP, MANUAL_STEP -> external(deployment.metamodelId, flow, node, event, variables);
        };
    }

    /** Descripción: Ejecuta la operación «waitForEvent» correspondiente a la responsabilidad de NodeOutcome. Entradas: state: dato de entrada necesario para la operación; flow: dato de entrada necesario para la operación; node: dato de entrada necesario para la operación; event: dato de entrada necesario para la operación; variables: dato de entrada necesario para la operación; compensableNodes: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private NodeOutcome waitForEvent(EventFlowRuntimeState state, FlowDefinition flow, FlowNode node, CanonicalEvent event,
            Map<String, Object> variables, Deque<String> compensableNodes) {
        if(node.eventTypeId().isBlank()) return NodeOutcome.failure(event, "Wait node requires an event type");
        if(node.eventTypeId().equals(event.eventTypeId())) return NodeOutcome.pass(event, "Awaited event received");
        String key = groupingKey(node, event);
        Instant now = Instant.now();
        Instant deadline = node.timeoutPolicy().timeoutMillis() <= 0 ? null : now.plusMillis(node.timeoutPolicy().timeoutMillis());
        FlowWaitingContinuation continuation = new FlowWaitingContinuation("wait:" + UUID.randomUUID(), flow.id(), node.id(),
                node.eventTypeId(), key, now, deadline, event, variables, List.copyOf(compensableNodes));
        state.waitingContinuations.put(continuation.id(), continuation);
        return NodeOutcome.waiting(event, "Waiting for " + node.eventTypeId());
    }

    /** Descripción: Ejecuta la operación «deduplicate» correspondiente a la responsabilidad de NodeOutcome. Entradas: state: dato de entrada necesario para la operación; flow: dato de entrada necesario para la operación; node: dato de entrada necesario para la operación; event: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private NodeOutcome deduplicate(EventFlowRuntimeState state, FlowDefinition flow, FlowNode node, CanonicalEvent event) {
        String path = EventValueResolver.text(node.configuration().get("keyPath"), "id");
        String key = String.valueOf(EventValueResolver.read(event, path));
        long ttl = EventValueResolver.longValue(node.configuration().get("ttlMillis"), 0L);
        String stateKey = flow.id() + ":" + node.id();
        LinkedHashMap<String, Instant> seen = state.deduplication.computeIfAbsent(stateKey, ignored -> new LinkedHashMap<>());
        Instant now = Instant.now();
        if(ttl > 0) seen.entrySet().removeIf(entry -> entry.getValue().plusMillis(ttl).isBefore(now));
        if(seen.containsKey(key)) return NodeOutcome.filtered(event, "Duplicate event key: " + key);
        seen.put(key, now);
        trim(seen, 4096);
        return NodeOutcome.pass(event, "Deduplication key accepted");
    }

    /** Descripción: Procesa la operación «emit» coordinando las responsabilidades de NodeOutcome. Entradas: metamodelId: identificador que delimita el recurso objetivo; flow: dato de entrada necesario para la operación; node: dato de entrada necesario para la operación; event: dato de entrada necesario para la operación; depth: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private NodeOutcome emit(String metamodelId, FlowDefinition flow, FlowNode node, CanonicalEvent event, int depth) {
        String eventType = node.eventTypeId().isBlank()
                ? EventValueResolver.text(node.configuration().get("targetEventTypeId"), "") : node.eventTypeId();
        if(eventType.isBlank()) return NodeOutcome.failure(event, "EMIT node has no target event type");
        CanonicalEvent derived = EventFlowRuntimeEventFactory.derived(metamodelId, flow.id(), node.id(), eventType, event,
                withRuntimePayload(event.payload(), Map.of("sourceFlowId", flow.id(), "sourceNodeId", node.id())), depth);
        return new NodeOutcome(true, false, derived, List.of(derived), "Derived event emitted", false, false);
    }

    /** Descripción: Ejecuta la operación «window» correspondiente a la responsabilidad de NodeOutcome. Entradas: state: dato de entrada necesario para la operación; flow: dato de entrada necesario para la operación; node: dato de entrada necesario para la operación; event: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private NodeOutcome window(EventFlowRuntimeState state, FlowDefinition flow, FlowNode node, CanonicalEvent event) {
        int size = Math.max(1, EventValueResolver.integer(node.configuration().get("size"), 2));
        boolean sliding = Boolean.parseBoolean(EventValueResolver.text(node.configuration().get("sliding"), "false"));
        String key = flow.id() + ":" + node.id() + ":" + groupingKey(node, event);
        Deque<CanonicalEvent> window = state.windows.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        window.addLast(event);
        while(window.size() > size) window.removeFirst();
        if(window.size() < size) return NodeOutcome.waiting(event, "Window contains " + window.size() + " of " + size + " events");
        List<Map<String, Object>> payloads = window.stream().map(CanonicalEvent::payload).toList();
        CanonicalEvent mapped = withPayload(event, withRuntimePayload(event.payload(), Map.of("windowSize", window.size(), "windowPayloads", payloads)));
        if(!sliding) window.clear();
        return NodeOutcome.pass(mapped, "Window completed");
    }

    /** Descripción: Ejecuta la operación «aggregate» correspondiente a la responsabilidad de NodeOutcome. Entradas: state: dato de entrada necesario para la operación; flow: dato de entrada necesario para la operación; node: dato de entrada necesario para la operación; event: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private NodeOutcome aggregate(EventFlowRuntimeState state, FlowDefinition flow, FlowNode node, CanonicalEvent event) {
        String key = flow.id() + ":" + node.id() + ":" + groupingKey(node, event);
        long count = state.aggregates.merge(key, 1L, Long::sum);
        String sumPath = EventValueResolver.text(node.configuration().get("sumPath"), "");
        double sum = state.sums.getOrDefault(key, 0.0d);
        if(!sumPath.isBlank()) {
            Object value = EventValueResolver.read(event, sumPath);
            try { sum += Double.parseDouble(String.valueOf(value)); } catch(NumberFormatException ignored){com.example.editor.observability.HydraBestEffortDiagnostics.record("com/example/editor/event/flow/runtime/EventFlowRuntimeNodeExecutor.java",ignored);}
            state.sums.put(key, sum);
        }
        Map<String, Object> updates = new LinkedHashMap<>();
        updates.put("aggregateCount", count);
        if(!sumPath.isBlank()) updates.put("aggregateSum", sum);
        return NodeOutcome.pass(withPayload(event, withRuntimePayload(event.payload(), updates)), "Aggregate updated");
    }

    /** Descripción: Ejecuta la operación «join» correspondiente a la responsabilidad de NodeOutcome. Entradas: state: dato de entrada necesario para la operación; flow: dato de entrada necesario para la operación; node: dato de entrada necesario para la operación; event: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private NodeOutcome join(EventFlowRuntimeState state, FlowDefinition flow, FlowNode node, CanonicalEvent event) {
        List<String> required = stringList(node.configuration().get("eventTypes"));
        if(required.isEmpty()) return NodeOutcome.pass(event, "Join has no required event types");
        String key = flow.id() + ":" + node.id() + ":" + groupingKey(node, event);
        Map<String, CanonicalEvent> joined = state.joins.computeIfAbsent(key, ignored -> new LinkedHashMap<>());
        joined.put(event.eventTypeId(), event);
        if(!joined.keySet().containsAll(required)) return NodeOutcome.waiting(event, "Join is waiting for " + difference(required, joined.keySet()));
        List<Map<String, Object>> payloads = required.stream().map(joined::get).filter(Objects::nonNull).map(CanonicalEvent::payload).toList();
        joined.clear();
        return NodeOutcome.pass(withPayload(event, withRuntimePayload(event.payload(), Map.of("joinedEventTypes", required, "joinedPayloads", payloads))), "Join completed");
    }

    /** Descripción: Ejecuta la operación «sequence» correspondiente a la responsabilidad de NodeOutcome. Entradas: state: dato de entrada necesario para la operación; flow: dato de entrada necesario para la operación; node: dato de entrada necesario para la operación; event: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private NodeOutcome sequence(EventFlowRuntimeState state, FlowDefinition flow, FlowNode node, CanonicalEvent event) {
        List<String> sequence = stringList(node.configuration().get("eventTypes"));
        if(sequence.isEmpty()) return NodeOutcome.failure(event, "Sequence requires eventTypes");
        String key = flow.id() + ":" + node.id() + ":" + groupingKey(node, event);
        int index = state.sequences.getOrDefault(key, 0);
        if(event.eventTypeId().equals(sequence.get(index))) index++;
        else if(event.eventTypeId().equals(sequence.get(0))) index = 1;
        else index = 0;
        if(index < sequence.size()) {
            state.sequences.put(key, index);
            return NodeOutcome.waiting(event, "Sequence progress " + index + "/" + sequence.size());
        }
        state.sequences.remove(key);
        return NodeOutcome.pass(withPayload(event, withRuntimePayload(event.payload(), Map.of("matchedSequence", sequence))), "Sequence completed");
    }

    /** Descripción: Ejecuta la operación «pattern» correspondiente a la responsabilidad de NodeOutcome. Entradas: state: dato de entrada necesario para la operación; flow: dato de entrada necesario para la operación; node: dato de entrada necesario para la operación; event: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private NodeOutcome pattern(EventFlowRuntimeState state, FlowDefinition flow, FlowNode node, CanonicalEvent event) {
        List<String> required = stringList(node.configuration().get("eventTypes"));
        if(required.isEmpty()) return NodeOutcome.failure(event, "Pattern requires eventTypes");
        String key = flow.id() + ":" + node.id() + ":" + groupingKey(node, event);
        Set<String> seen = state.patterns.computeIfAbsent(key, ignored -> new LinkedHashSet<>());
        if(required.contains(event.eventTypeId())) seen.add(event.eventTypeId());
        if(!seen.containsAll(required)) return NodeOutcome.waiting(event, "Pattern is waiting for " + difference(required, seen));
        seen.clear();
        return NodeOutcome.pass(withPayload(event, withRuntimePayload(event.payload(), Map.of("matchedPattern", required))), "Pattern completed");
    }

    /** Descripción: Ejecuta la operación «absence» correspondiente a la responsabilidad de NodeOutcome. Entradas: state: dato de entrada necesario para la operación; flow: dato de entrada necesario para la operación; node: dato de entrada necesario para la operación; event: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private NodeOutcome absence(EventFlowRuntimeState state, FlowDefinition flow, FlowNode node, CanonicalEvent event) {
        long timeout = Math.max(1L, EventValueResolver.longValue(node.configuration().get("timeoutMillis"), node.timeoutPolicy().timeoutMillis()));
        String watchEvent = EventValueResolver.text(node.configuration().get("watchEventTypeId"), "");
        String outputEvent = node.eventTypeId();
        if(watchEvent.isBlank() || outputEvent.isBlank()) return NodeOutcome.failure(event, "Absence requires watchEventTypeId and output eventTypeId");
        String key = groupingKey(node, event);
        Instant created = Instant.now();
        String groupByPath = EventValueResolver.text(node.configuration().get("groupByPath"), "correlationId");
        FlowAbsenceContinuation watch = new FlowAbsenceContinuation(flow.id() + ":" + node.id() + ":" + key, flow.id(), node.id(), key, groupByPath,
                watchEvent, outputEvent, created, created.plusMillis(timeout), event);
        state.absenceWatches.put(watch.id(), watch);
        return NodeOutcome.waiting(event, "Absence watch scheduled until " + watch.deadline());
    }

    /** Descripción: Ejecuta la operación «external» correspondiente a la responsabilidad de NodeOutcome. Entradas: metamodelId: identificador que delimita el recurso objetivo; flow: dato de entrada necesario para la operación; node: dato de entrada necesario para la operación; event: dato de entrada necesario para la operación; variables: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private NodeOutcome external(String metamodelId, FlowDefinition flow, FlowNode node, CanonicalEvent event,
            Map<String, Object> variables) {
        for(FlowExternalStepExecutor executor : externalExecutors) {
            if(executor.supports(node.kind(), node.referenceId())) {
                FlowExternalStepResult result = executor.execute(new FlowExternalStepContext(metamodelId, flow, node, event, Map.copyOf(variables)));
                CanonicalEvent updated = result.payloadUpdates().isEmpty() ? event
                        : withPayload(event, withRuntimePayload(event.payload(), result.payloadUpdates()));
                return result.successful() ? NodeOutcome.pass(updated, result.message()) : NodeOutcome.failure(updated, result.message());
            }
        }
        boolean allowNoop = Boolean.parseBoolean(EventValueResolver.text(node.configuration().get("allowNoop"), "false"));
        return allowNoop ? NodeOutcome.pass(event, "No external executor registered; node accepted as explicit no-op")
                : NodeOutcome.failure(event, "No FlowExternalStepExecutor supports " + node.kind() + " / " + node.referenceId());
    }

    record NodeOutcome(boolean success, boolean waiting, CanonicalEvent event, List<CanonicalEvent> emitted,
            String message, boolean filtered, boolean timedOut) {
        /** Descripción: Inicializa una instancia de NodeOutcome con sus dependencias y datos de entrada. Entradas: success: dato de entrada necesario para la operación; waiting: dato de entrada necesario para la operación; event: dato de entrada necesario para la operación; emitted: dato de entrada necesario para la operación; message: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ NodeOutcome(boolean success, boolean waiting, CanonicalEvent event, List<CanonicalEvent> emitted, String message) {
            this(success, waiting, event, emitted, message, false, false);
        }
        /** Descripción: Ejecuta la operación «pass» correspondiente a la responsabilidad de NodeOutcome. Entradas: event: dato de entrada necesario para la operación; message: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ static NodeOutcome pass(CanonicalEvent event, String message) { return new NodeOutcome(true, false, event, List.of(), message, false, false); }
        /** Descripción: Ejecuta la operación «filtered» correspondiente a la responsabilidad de NodeOutcome. Entradas: event: dato de entrada necesario para la operación; message: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ static NodeOutcome filtered(CanonicalEvent event, String message) { return new NodeOutcome(true, false, event, List.of(), message, true, false); }
        /** Descripción: Ejecuta la operación «waiting» correspondiente a la responsabilidad de NodeOutcome. Entradas: event: dato de entrada necesario para la operación; message: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ static NodeOutcome waiting(CanonicalEvent event, String message) { return new NodeOutcome(true, true, event, List.of(), message, false, false); }
        /** Descripción: Ejecuta la operación «failure» correspondiente a la responsabilidad de NodeOutcome. Entradas: event: dato de entrada necesario para la operación; message: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ static NodeOutcome failure(CanonicalEvent event, String message) { return new NodeOutcome(false, false, event, List.of(), message, false, false); }
        /** Descripción: Ejecuta la operación «timeout» correspondiente a la responsabilidad de NodeOutcome. Entradas: event: dato de entrada necesario para la operación; message: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ static NodeOutcome timeout(CanonicalEvent event, String message) { return new NodeOutcome(false, false, event, List.of(), message, false, true); }
    }
}
