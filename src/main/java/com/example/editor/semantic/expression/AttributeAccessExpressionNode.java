package com.example.editor.semantic.expression;

import com.example.editor.contract.StableIdentifierPolicy;
import com.fasterxml.jackson.annotation.JsonTypeName;

@JsonTypeName("ATTRIBUTE_ACCESS")
public record AttributeAccessExpressionNode(String nodeId, ExpressionNode target, String attributeId) implements ExpressionNode {
    public AttributeAccessExpressionNode {
        nodeId = StableIdentifierPolicy.requireElementId(nodeId, "nodeId");
        if(target == null) throw new IllegalArgumentException("target must not be null");
        attributeId = StableIdentifierPolicy.requireElementId(attributeId, "attributeId");
    }
}
