package com.example.editor.semantic.expression;

/** Resultado de evaluar una expresión canónica. */
public record ExpressionEvaluationResult(String expressionId, ExpressionValueType valueType, Object value) {
    public ExpressionEvaluationResult {
        expressionId = expressionId == null ? "" : expressionId;
        valueType = valueType == null ? ExpressionValueType.scalar(ExpressionValueKind.ANY) : valueType;
    }
}
