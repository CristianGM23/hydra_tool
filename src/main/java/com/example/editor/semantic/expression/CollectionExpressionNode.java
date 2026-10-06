package com.example.editor.semantic.expression;

import com.example.editor.contract.StableIdentifierPolicy;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonTypeName("COLLECTION")
public record CollectionExpressionNode(String nodeId, CollectionExpressionOperator operator, ExpressionNode source, ExpressionNode expression) implements ExpressionNode {
    public CollectionExpressionNode {
        nodeId = StableIdentifierPolicy.requireElementId(nodeId, "nodeId");
        if(operator == null) throw new IllegalArgumentException("operator must not be null");
        if(source == null) throw new IllegalArgumentException("source must not be null");
    }
}
