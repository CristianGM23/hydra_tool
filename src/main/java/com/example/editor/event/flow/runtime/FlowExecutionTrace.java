package com.example.editor.event.flow.runtime;

import java.time.Instant;
import java.util.List;

/** Trazabilidad de una ejecución individual de flujo. */
public record FlowExecutionTrace(String id, String flowId, String sourceEventId, Instant startedAt, Instant completedAt,
        FlowExecutionStatus status, List<FlowNodeExecution> nodes, List<String> emittedEventIds, String message) {
    public FlowExecutionTrace {
        if(startedAt == null) startedAt = Instant.now();
        if(completedAt == null) completedAt = Instant.now();
        nodes = nodes == null ? List.of() : List.copyOf(nodes);
        emittedEventIds = emittedEventIds == null ? List.of() : List.copyOf(emittedEventIds);
        message = message == null ? "" : message;
    }
}
