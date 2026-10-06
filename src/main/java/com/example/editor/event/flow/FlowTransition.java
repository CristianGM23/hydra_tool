package com.example.editor.event.flow;

import com.example.editor.validation.contract.StableIdentifierPolicy;

/** Arista semántica entre dos nodos de un flujo. */
public record FlowTransition(String id, String sourceNodeId, String targetNodeId, FlowTransitionKind kind,
        String conditionExpressionId, int order, String label) {
    public FlowTransition {
        id = StableIdentifierPolicy.requireElementId(id, "flowTransition.id");
        sourceNodeId = StableIdentifierPolicy.requireElementId(sourceNodeId, "flowTransition.sourceNodeId");
        targetNodeId = StableIdentifierPolicy.requireElementId(targetNodeId, "flowTransition.targetNodeId");
        if(kind == null) kind = FlowTransitionKind.NEXT;
        conditionExpressionId = conditionExpressionId == null ? "" : conditionExpressionId.trim();
        label = label == null ? "" : label.trim();
    }
}
