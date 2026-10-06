package com.example.editor.semantic.expression;

import java.util.List;

import com.example.editor.contract.StableIdentifierPolicy;
import com.fasterxml.jackson.annotation.JsonTypeName;

/** Invoca una operación QUERY declarada en el metamodelo. */
@JsonTypeName("OPERATION_CALL")
public record OperationCallExpressionNode(String nodeId, ExpressionNode target, String operationId, List<ExpressionNode> arguments) implements ExpressionNode {
    public OperationCallExpressionNode {
        nodeId = StableIdentifierPolicy.requireElementId(nodeId, "nodeId");
        if(target == null) throw new IllegalArgumentException("target must not be null");
        operationId = StableIdentifierPolicy.requireElementId(operationId, "operationId");
        arguments = arguments == null ? List.of() : List.copyOf(arguments);
    }
}
