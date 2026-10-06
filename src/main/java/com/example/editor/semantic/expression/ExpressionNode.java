package com.example.editor.semantic.expression;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/** Nodo base del árbol canónico de expresiones. */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "kind")
@JsonSubTypes({
    @JsonSubTypes.Type(value = LiteralExpressionNode.class, name = "LITERAL"),
    @JsonSubTypes.Type(value = SelfExpressionNode.class, name = "SELF"),
    @JsonSubTypes.Type(value = ModelExpressionNode.class, name = "MODEL"),
    @JsonSubTypes.Type(value = ClassInstancesExpressionNode.class, name = "CLASS_INSTANCES"),
    @JsonSubTypes.Type(value = AttributeAccessExpressionNode.class, name = "ATTRIBUTE_ACCESS"),
    @JsonSubTypes.Type(value = RelationNavigationExpressionNode.class, name = "RELATION_NAVIGATION"),
    @JsonSubTypes.Type(value = UnaryExpressionNode.class, name = "UNARY"),
    @JsonSubTypes.Type(value = BinaryExpressionNode.class, name = "BINARY"),
    @JsonSubTypes.Type(value = CollectionExpressionNode.class, name = "COLLECTION"),
    @JsonSubTypes.Type(value = ConditionalExpressionNode.class, name = "CONDITIONAL"),
    @JsonSubTypes.Type(value = FunctionExpressionNode.class, name = "FUNCTION"),
    @JsonSubTypes.Type(value = VariableExpressionNode.class, name = "VARIABLE"),
    @JsonSubTypes.Type(value = OperationCallExpressionNode.class, name = "OPERATION_CALL")
})
public sealed interface ExpressionNode permits LiteralExpressionNode, SelfExpressionNode, ModelExpressionNode, ClassInstancesExpressionNode, AttributeAccessExpressionNode, RelationNavigationExpressionNode, UnaryExpressionNode, BinaryExpressionNode, CollectionExpressionNode, ConditionalExpressionNode, FunctionExpressionNode, VariableExpressionNode, OperationCallExpressionNode {
    /** Descripción: Ejecuta la operación «nodeId» correspondiente a la responsabilidad de ExpressionNode. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ String nodeId();
}
