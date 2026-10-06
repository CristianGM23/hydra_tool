package com.example.editor.semantic.expression;

import com.example.editor.contract.StableIdentifierPolicy;
import com.fasterxml.jackson.annotation.JsonTypeName;

@JsonTypeName("LITERAL")
public record LiteralExpressionNode(String nodeId, ExpressionValueType valueType, Object value) implements ExpressionNode {
    public LiteralExpressionNode {
        nodeId = StableIdentifierPolicy.requireElementId(nodeId, "nodeId");
        valueType = valueType == null ? ExpressionValueType.scalar(ExpressionValueKind.ANY) : valueType;
    }
}
