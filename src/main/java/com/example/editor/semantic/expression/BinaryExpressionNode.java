package com.example.editor.semantic.expression;

import com.example.editor.contract.StableIdentifierPolicy;
import com.fasterxml.jackson.annotation.JsonTypeName;

@JsonTypeName("BINARY")
public record BinaryExpressionNode(String nodeId, BinaryExpressionOperator operator, ExpressionNode left, ExpressionNode right) implements ExpressionNode {
    public BinaryExpressionNode {
        nodeId = StableIdentifierPolicy.requireElementId(nodeId, "nodeId");
        if(operator == null) throw new IllegalArgumentException("operator must not be null");
        if(left == null || right == null) throw new IllegalArgumentException("left and right must not be null");
    }
}
