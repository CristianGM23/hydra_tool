package com.example.editor.semantic.expression;

import com.example.editor.contract.StableIdentifierPolicy;
import com.fasterxml.jackson.annotation.JsonTypeName;

@JsonTypeName("UNARY")
public record UnaryExpressionNode(String nodeId, UnaryExpressionOperator operator, ExpressionNode operand) implements ExpressionNode {
    public UnaryExpressionNode {
        nodeId = StableIdentifierPolicy.requireElementId(nodeId, "nodeId");
        if(operator == null) throw new IllegalArgumentException("operator must not be null");
        if(operand == null) throw new IllegalArgumentException("operand must not be null");
    }
}
