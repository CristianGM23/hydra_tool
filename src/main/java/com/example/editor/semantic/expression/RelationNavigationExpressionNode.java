package com.example.editor.semantic.expression;

import com.example.editor.contract.StableIdentifierPolicy;
import com.fasterxml.jackson.annotation.JsonTypeName;

@JsonTypeName("RELATION_NAVIGATION")
public record RelationNavigationExpressionNode(String nodeId, ExpressionNode target, String relationId) implements ExpressionNode {
    public RelationNavigationExpressionNode {
        nodeId = StableIdentifierPolicy.requireElementId(nodeId, "nodeId");
        if(target == null) throw new IllegalArgumentException("target must not be null");
        relationId = StableIdentifierPolicy.requireElementId(relationId, "relationId");
    }
}
