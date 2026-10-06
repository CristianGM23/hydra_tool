package com.example.editor.semantic.expression;

import com.example.editor.contract.StableIdentifierPolicy;
import com.fasterxml.jackson.annotation.JsonTypeName;

/** Lee una variable proporcionada por el runtime, por ejemplo un parámetro de operación. */
@JsonTypeName("VARIABLE")
public record VariableExpressionNode(String nodeId, String variableName) implements ExpressionNode {
    public VariableExpressionNode {
        nodeId = StableIdentifierPolicy.requireElementId(nodeId, "nodeId");
        variableName = StableIdentifierPolicy.requireElementId(variableName, "variableName");
    }
}
