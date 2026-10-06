package com.example.editor.event.flow.runtime;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.example.editor.event.CanonicalEvent;
/** Punto reanudable persistente creado por WAIT_FOR_EVENT. */
public record FlowWaitingContinuation(String id, String flowId, String waitNodeId, String awaitedEventTypeId,
        String groupKey, Instant createdAt, Instant deadline, CanonicalEvent sourceEvent,
        Map<String,Object> variables, List<String> compensableNodeIds) {
    public FlowWaitingContinuation {
        id = id == null ? "" : id.trim(); flowId = flowId == null ? "" : flowId.trim();
        waitNodeId = waitNodeId == null ? "" : waitNodeId.trim(); awaitedEventTypeId = awaitedEventTypeId == null ? "" : awaitedEventTypeId.trim();
        groupKey = groupKey == null ? "" : groupKey.trim(); if(createdAt == null) createdAt = Instant.now();
        variables = variables == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(variables));
        compensableNodeIds = compensableNodeIds == null ? List.of() : List.copyOf(compensableNodeIds);
    }
}
