package com.example.editor.semantic.expression;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Stable structural identity for reusable expressions. Generated node IDs and
 * presentation metadata are ignored, so two independently-created copies of
 * the same condition compare equal.
 */
public final class ExpressionSemanticFingerprint {
    /** Descripción: Inicializa una instancia de ExpressionSemanticFingerprint con sus dependencias y datos de entrada. Entradas: no requiere parámetros de entrada. Salida: deja la instancia inicializada y lista para su uso. */ private ExpressionSemanticFingerprint() { }

    /** Descripción: Ejecuta la operación «of» correspondiente a la responsabilidad de ExpressionSemanticFingerprint. Entradas: expression: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static String of(ExpressionDefinition expression) {
        if(expression == null) return "";
        return "ctx=" + safe(expression.contextClassId()) + "|type=" + type(expression.declaredReturnType()) + "|root=" + node(expression.root());
    }

    /** Descripción: Ejecuta la operación «node» correspondiente a la responsabilidad de ExpressionSemanticFingerprint. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String node(ExpressionNode value) {
        return switch(value) {
            case LiteralExpressionNode item -> "LITERAL(" + type(item.valueType()) + "," + literal(item.value()) + ")";
            case SelfExpressionNode ignored -> "SELF";
            case ModelExpressionNode ignored -> "MODEL";
            case ClassInstancesExpressionNode item -> "CLASS_INSTANCES(" + safe(item.classId()) + ")";
            case AttributeAccessExpressionNode item -> "ATTRIBUTE_ACCESS(" + node(item.target()) + "," + safe(item.attributeId()) + ")";
            case RelationNavigationExpressionNode item -> "RELATION_NAVIGATION(" + node(item.target()) + "," + safe(item.relationId()) + ")";
            case UnaryExpressionNode item -> "UNARY(" + item.operator() + "," + node(item.operand()) + ")";
            case BinaryExpressionNode item -> "BINARY(" + item.operator() + "," + node(item.left()) + "," + node(item.right()) + ")";
            case CollectionExpressionNode item -> "COLLECTION(" + item.operator() + "," + node(item.source()) + "," + (item.expression() == null ? "" : node(item.expression())) + ")";
            case ConditionalExpressionNode item -> "CONDITIONAL(" + node(item.condition()) + "," + node(item.whenTrue()) + "," + node(item.whenFalse()) + ")";
            case FunctionExpressionNode item -> "FUNCTION(" + safe(item.functionName()) + "," + nodes(item.arguments()) + ")";
            case VariableExpressionNode item -> "VARIABLE(" + safe(item.variableName()) + ")";
            case OperationCallExpressionNode item -> "OPERATION_CALL(" + node(item.target()) + "," + safe(item.operationId()) + "," + nodes(item.arguments()) + ")";
        };
    }

    /** Descripción: Ejecuta la operación «nodes» correspondiente a la responsabilidad de ExpressionSemanticFingerprint. Entradas: values: colección de elementos que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String nodes(List<ExpressionNode> values) {
        return values.stream().map(ExpressionSemanticFingerprint::node).reduce((left, right) -> left + "," + right).orElse("");
    }

    /** Descripción: Ejecuta la operación «type» correspondiente a la responsabilidad de ExpressionSemanticFingerprint. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String type(ExpressionValueType value) {
        if(value == null) return "";
        return value.kind() + ":" + safe(value.classId()) + ":" + safe(value.enumId()) + ":" + (value.elementType() == null ? "" : type(value.elementType()));
    }

    /** Descripción: Ejecuta la operación «literal» correspondiente a la responsabilidad de ExpressionSemanticFingerprint. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String literal(Object value) {
        if(value == null) return "null";
        if(value instanceof Map<?,?> map) {
            List<Map.Entry<?,?>> entries = new ArrayList<>(map.entrySet());
            entries.sort(Comparator.comparing(entry -> String.valueOf(entry.getKey())));
            return "{" + entries.stream().map(entry -> safe(String.valueOf(entry.getKey())) + ":" + literal(entry.getValue())).reduce((left, right) -> left + "," + right).orElse("") + "}";
        }
        if(value instanceof Iterable<?> iterable) {
            List<String> items = new ArrayList<>();
            for(Object item : iterable) items.add(literal(item));
            return "[" + String.join(",", items) + "]";
        }
        return value.getClass().getName() + ":" + String.valueOf(value);
    }

    /** Descripción: Ejecuta la operación «safe» correspondiente a la responsabilidad de ExpressionSemanticFingerprint. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String safe(String value) { return value == null ? "" : value.replace("\\", "\\\\").replace("|", "\\|").replace(",", "\\,"); }
}
