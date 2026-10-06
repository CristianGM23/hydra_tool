package com.example.editor.validation.expression;

public record ExpressionModelLoadResult(ExpressionModel expressionModel, String etag) {
    public ExpressionModelLoadResult { etag = etag == null ? "" : etag; }
}
