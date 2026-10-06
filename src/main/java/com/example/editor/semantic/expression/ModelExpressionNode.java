package com.example.editor.semantic.expression;

import com.example.editor.contract.StableIdentifierPolicy;
import com.fasterxml.jackson.annotation.JsonTypeName;

@JsonTypeName("MODEL")
public record ModelExpressionNode(String nodeId) implements ExpressionNode {
    public ModelExpressionNode {
        nodeId = StableIdentifierPolicy.requireElementId(nodeId, "nodeId");
    }
}
