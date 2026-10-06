package com.example.editor.semantic.expression;

import com.example.editor.contract.StableIdentifierPolicy;
import com.fasterxml.jackson.annotation.JsonTypeName;

@JsonTypeName("CLASS_INSTANCES")
public record ClassInstancesExpressionNode(String nodeId, String classId) implements ExpressionNode {
    public ClassInstancesExpressionNode {
        nodeId = StableIdentifierPolicy.requireElementId(nodeId, "nodeId");
        classId = StableIdentifierPolicy.requireElementId(classId, "classId");
    }
}
