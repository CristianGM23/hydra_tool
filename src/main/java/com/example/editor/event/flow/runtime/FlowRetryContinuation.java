package com.example.editor.event.flow.runtime;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.example.editor.event.CanonicalEvent;

/** Reintento durable de un nodo, reanudado por tick cuando vence su backoff. */
public record FlowRetryContinuation(String id, String flowId, String nodeId, int attempt, int maximumAttempts,
        Instant dueAt, Instant startedAt, CanonicalEvent sourceEvent, CanonicalEvent currentEvent,
        Map<String,Object> variables, List<String> compensableNodeIds, List<FlowNodeExecution> executions) {
    public FlowRetryContinuation {
        id = id == null ? "" : id.trim();
        flowId = flowId == null ? "" : flowId.trim();
        nodeId = nodeId == null ? "" : nodeId.trim();
        if(attempt < 1) attempt = 1;
        if(maximumAttempts < attempt) maximumAttempts = attempt;
        if(dueAt == null) dueAt = Instant.now();
        if(startedAt == null) startedAt = Instant.now();
        variables = variables == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(variables));
        compensableNodeIds = compensableNodeIds == null ? List.of() : List.copyOf(compensableNodeIds);
        executions = executions == null ? List.of() : List.copyOf(executions);
    }
}
