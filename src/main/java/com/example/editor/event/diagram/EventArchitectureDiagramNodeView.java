package com.example.editor.event.diagram;

import com.example.editor.validation.contract.StableIdentifierPolicy;

/** Geometría de un tipo de evento reservado o personalizado. */
public record EventArchitectureDiagramNodeView(String nodeId, double x, double y, double width, double height,
        boolean collapsed) {
    public EventArchitectureDiagramNodeView {
        nodeId = StableIdentifierPolicy.requireElementId(nodeId, "eventArchitectureDiagramNode.nodeId");
        if(width <= 0) width = 230;
        if(height <= 0) height = 92;
    }
}
