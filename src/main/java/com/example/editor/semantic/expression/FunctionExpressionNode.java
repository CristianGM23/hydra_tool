package com.example.editor.semantic.expression;

import java.util.List;

import com.example.editor.contract.StableIdentifierPolicy;
import com.fasterxml.jackson.annotation.JsonTypeName;

@JsonTypeName("FUNCTION")
public record FunctionExpressionNode(String nodeId, String functionName, List<ExpressionNode> arguments) implements ExpressionNode {
    public FunctionExpressionNode {
        nodeId = StableIdentifierPolicy.requireElementId(nodeId, "nodeId");
        if(functionName == null || functionName.isBlank()) throw new IllegalArgumentException("functionName must not be blank");
        arguments = arguments == null ? List.of() : List.copyOf(arguments);
    }
}
