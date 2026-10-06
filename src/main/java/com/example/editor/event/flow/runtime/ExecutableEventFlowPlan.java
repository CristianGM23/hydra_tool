package com.example.editor.event.flow.runtime;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import com.example.editor.event.flow.EventFlowContractVersions;

/** Plan derivado; nunca sustituye al EventFlowModel fuente. */
public record ExecutableEventFlowPlan(String schemaVersion, String id, String metamodelId, String sourceModelId,
        Instant compiledAt, List<ExecutableFlowDefinition> flows, Map<String, List<String>> flowsByTriggerEventType) {
    public ExecutableEventFlowPlan {
        if(schemaVersion == null || schemaVersion.isBlank()) schemaVersion = EventFlowContractVersions.EXECUTABLE_EVENT_FLOW_PLAN;
        flows = flows == null ? List.of() : List.copyOf(flows);
        flowsByTriggerEventType = flowsByTriggerEventType == null ? Map.of() : Map.copyOf(flowsByTriggerEventType);
        if(compiledAt == null) compiledAt = Instant.now();
    }
}
