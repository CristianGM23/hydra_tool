package com.example.editor.validation.expression;

public record ExpressionModelSummary(String id, String metamodelId, String schemaVersion, int expressionCount, String etag) {
    public ExpressionModelSummary { etag = etag == null ? "" : etag; }
}
