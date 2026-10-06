package com.example.editor.event.flow.runtime;

import java.util.List;
import java.util.Map;
import com.example.editor.event.flow.CompensationLink;
import com.example.editor.event.flow.FlowDefinition;

/** Flujo compilado e indexado para ejecución. */
public record ExecutableFlowDefinition(FlowDefinition source, String startNodeId, Map<String, ExecutableFlowNode> nodes,
        List<CompensationLink> compensations) {
    public ExecutableFlowDefinition {
        nodes = nodes == null ? Map.of() : Map.copyOf(nodes);
        compensations = compensations == null ? List.of() : List.copyOf(compensations);
    }
}
