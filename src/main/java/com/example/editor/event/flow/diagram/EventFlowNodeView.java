package com.example.editor.event.flow.diagram;

import com.example.editor.validation.contract.StableIdentifierPolicy;

/** Geometría separada de un nodo del Event Flow Designer. */
public record EventFlowNodeView(String nodeId, double x, double y, double width, double height, boolean collapsed) {
    public EventFlowNodeView {
        nodeId = StableIdentifierPolicy.requireElementId(nodeId, "eventFlowNodeView.nodeId");
        if(width <= 0) width = 180;
        if(height <= 0) height = 72;
    }
}
