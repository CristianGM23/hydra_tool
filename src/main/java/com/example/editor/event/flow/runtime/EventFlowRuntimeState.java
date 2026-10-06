package com.example.editor.event.flow.runtime;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.example.editor.event.CanonicalEvent;

/** Estado mutable de una ejecución EventFlow, aislado de la orquestación del servicio. */
final class EventFlowRuntimeState {
    final Map<String, LinkedHashMap<String, Instant>> deduplication = new LinkedHashMap<>();
    final Map<String, Deque<CanonicalEvent>> windows = new LinkedHashMap<>();
    final Map<String, Long> aggregates = new LinkedHashMap<>();
    final Map<String, Double> sums = new LinkedHashMap<>();
    final Map<String, Map<String, CanonicalEvent>> joins = new LinkedHashMap<>();
    final Map<String, Integer> sequences = new LinkedHashMap<>();
    final Map<String, Set<String>> patterns = new LinkedHashMap<>();
    final Map<String, FlowAbsenceContinuation> absenceWatches = new LinkedHashMap<>();
    final Map<String, FlowWaitingContinuation> waitingContinuations = new LinkedHashMap<>();
    final Map<String, FlowRetryContinuation> retryContinuations = new LinkedHashMap<>();
    final List<FlowExecutionTrace> traces = new ArrayList<>();

    /** Descripción: Ejecuta la operación «size» correspondiente a la responsabilidad de EventFlowRuntimeState. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ int size() {
        return deduplication.values().stream().mapToInt(Map::size).sum()
                + windows.values().stream().mapToInt(Collection::size).sum()
                + aggregates.size() + sums.size() + joins.values().stream().mapToInt(Map::size).sum()
                + sequences.size() + patterns.values().stream().mapToInt(Set::size).sum()
                + absenceWatches.size() + waitingContinuations.size() + retryContinuations.size();
    }

    /** Descripción: Ejecuta la operación «trim» correspondiente a la responsabilidad de EventFlowRuntimeState. Entradas: maximum: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ void trim(int maximum) {
        while(size() > maximum) {
            if(removeFirst(retryContinuations) || removeFirst(waitingContinuations) || removeFirst(absenceWatches)
                    || removeFirst(patterns) || removeFirst(sequences) || removeFirst(joins)
                    || removeFirst(sums) || removeFirst(aggregates) || removeFirst(windows) || removeFirst(deduplication)) continue;
            break;
        }
    }

    /** Descripción: Ejecuta la operación «snapshot» correspondiente a la responsabilidad de EventFlowRuntimeState. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ EventFlowRuntimeStateDocument snapshot() {
        Map<String, List<CanonicalEvent>> windowLists = new LinkedHashMap<>();
        windows.forEach((key, value) -> windowLists.put(key, List.copyOf(value)));
        Map<String, Map<String, Instant>> deduplicationCopy = new LinkedHashMap<>();
        deduplication.forEach((key, value) -> deduplicationCopy.put(key, new LinkedHashMap<>(value)));
        return new EventFlowRuntimeStateDocument(deduplicationCopy, windowLists, aggregates, sums, joins, sequences,
                patterns, List.copyOf(absenceWatches.values()));
    }

    /** Descripción: Ejecuta la operación «restore» correspondiente a la responsabilidad de EventFlowRuntimeState. Entradas: durable: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ void restore(EventFlowRuntimeStateDocument durable) {
        if(durable == null) return;
        durable.deduplication().forEach((key, value) -> deduplication.put(key, new LinkedHashMap<>(value)));
        durable.windows().forEach((key, value) -> windows.put(key, new ArrayDeque<>(value)));
        aggregates.putAll(durable.aggregates());
        sums.putAll(durable.sums());
        durable.joins().forEach((key, value) -> joins.put(key, new LinkedHashMap<>(value)));
        sequences.putAll(durable.sequences());
        durable.patterns().forEach((key, value) -> patterns.put(key, new LinkedHashSet<>(value)));
        durable.absenceWatches().forEach(value -> absenceWatches.put(value.id(), value));
    }

    /** Descripción: Elimina o libera el recurso gestionado por «clear» de forma controlada. Entradas: no requiere parámetros de entrada. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ void clear() {
        deduplication.clear(); windows.clear(); aggregates.clear(); sums.clear(); joins.clear(); sequences.clear();
        patterns.clear(); absenceWatches.clear(); waitingContinuations.clear(); retryContinuations.clear(); traces.clear();
    }

    /** Descripción: Elimina o libera el recurso gestionado por «removeFirst» de forma controlada. Entradas: map: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean removeFirst(Map<?, ?> map) {
        if(map.isEmpty()) return false;
        map.remove(map.keySet().iterator().next());
        return true;
    }
}
