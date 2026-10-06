package com.example.editor.semantic.expression;

import com.example.editor.contract.StableIdentifierPolicy;
import com.fasterxml.jackson.annotation.JsonTypeName;

@JsonTypeName("SELF")
public record SelfExpressionNode(String nodeId) implements ExpressionNode {
    public SelfExpressionNode {
        nodeId = StableIdentifierPolicy.requireElementId(nodeId, "nodeId");
    }
}
