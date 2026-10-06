package com.example.editor.event.diagram;

import java.util.List;
import com.example.editor.event.EventContractVersions;
import com.example.editor.validation.contract.StableIdentifierPolicy;

/** Documento visual separado de EventDefinitionModel y EventBindingModel. */
public record EventArchitectureDiagramDocument(String schemaVersion, String id, String metamodelId,
        String eventDefinitionModelId, String eventBindingModelId, List<EventArchitectureDiagramNodeView> nodes,
        List<EventArchitectureDiagramEdgeView> edges) {
    public EventArchitectureDiagramDocument {
        if(schemaVersion == null || schemaVersion.isBlank()) schemaVersion = EventContractVersions.EVENT_ARCHITECTURE_DIAGRAM;
        if(!EventContractVersions.EVENT_ARCHITECTURE_DIAGRAM.equals(schemaVersion)) {
            throw new IllegalArgumentException("Unsupported event-architecture diagram schemaVersion");
        }
        id = StableIdentifierPolicy.requireDocumentId(id, "eventArchitectureDiagram.id");
        metamodelId = StableIdentifierPolicy.requireDocumentId(metamodelId, "eventArchitectureDiagram.metamodelId");
        eventDefinitionModelId = StableIdentifierPolicy.requireDocumentId(eventDefinitionModelId, "eventArchitectureDiagram.eventDefinitionModelId");
        eventBindingModelId = StableIdentifierPolicy.requireDocumentId(eventBindingModelId, "eventArchitectureDiagram.eventBindingModelId");
        nodes = nodes == null ? List.of() : List.copyOf(nodes);
        edges = edges == null ? List.of() : List.copyOf(edges);
    }
}
