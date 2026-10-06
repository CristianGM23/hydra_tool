package com.example.editor.validation.expression;

public record ExpressionModelSaveResponse(ExpressionModel expressionModel, ExpressionModelCheckReport checkReport, String etag) {
    public ExpressionModelSaveResponse { etag = etag == null ? "" : etag; }
}
