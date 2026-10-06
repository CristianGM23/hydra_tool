package com.example.editor.event.flow.diagram;

import java.util.List;
import com.example.editor.event.flow.EventFlowContractVersions;
import com.example.editor.validation.contract.StableIdentifierPolicy;

/** Documento visual separado de la semántica de EventFlowModel. */
public record EventFlowDiagramDocument(String schemaVersion, String id, String metamodelId, String eventFlowModelId,
        List<EventFlowNodeView> nodes, List<EventFlowEdgeView> edges) {
    public EventFlowDiagramDocument {
        if(schemaVersion == null || schemaVersion.isBlank()) schemaVersion = EventFlowContractVersions.EVENT_FLOW_DIAGRAM;
        if(!EventFlowContractVersions.EVENT_FLOW_DIAGRAM.equals(schemaVersion)) throw new IllegalArgumentException("Unsupported event-flow diagram schemaVersion");
        id = StableIdentifierPolicy.requireDocumentId(id, "eventFlowDiagram.id");
        metamodelId = StableIdentifierPolicy.requireDocumentId(metamodelId, "eventFlowDiagram.metamodelId");
        eventFlowModelId = StableIdentifierPolicy.requireDocumentId(eventFlowModelId, "eventFlowDiagram.eventFlowModelId");
        nodes = nodes == null ? List.of() : List.copyOf(nodes);
        edges = edges == null ? List.of() : List.copyOf(edges);
    }
}
