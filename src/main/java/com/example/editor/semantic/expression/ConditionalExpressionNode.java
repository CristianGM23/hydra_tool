package com.example.editor.semantic.expression;

import com.example.editor.contract.StableIdentifierPolicy;
import com.fasterxml.jackson.annotation.JsonTypeName;

@JsonTypeName("CONDITIONAL")
public record ConditionalExpressionNode(String nodeId, ExpressionNode condition, ExpressionNode whenTrue, ExpressionNode whenFalse) implements ExpressionNode {
    public ConditionalExpressionNode {
        nodeId = StableIdentifierPolicy.requireElementId(nodeId, "nodeId");
        if(condition == null || whenTrue == null || whenFalse == null) throw new IllegalArgumentException("conditional operands must not be null");
    }
}
