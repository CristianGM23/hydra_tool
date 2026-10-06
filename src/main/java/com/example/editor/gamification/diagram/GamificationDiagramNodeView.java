package com.example.editor.gamification.diagram;

import com.example.editor.validation.contract.StableIdentifierPolicy;

/** Geometría de un elemento visual de gamificación, separada de los contratos canónicos. */
public record GamificationDiagramNodeView(String nodeId, double x, double y, double width, double height, boolean collapsed) {
    public GamificationDiagramNodeView {
        nodeId = StableIdentifierPolicy.requireElementId(nodeId, "gamificationDiagramNode.nodeId");
        if(width <= 0) width = 210;
        if(height <= 0) height = 84;
    }
}
