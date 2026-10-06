package com.example.editor.validation.expression.persistence;

import java.nio.file.Path;

import com.example.editor.validation.expression.ExpressionModel;

public record StoredExpressionModel(ExpressionModel expressionModel, String etag, Path path) {
    public StoredExpressionModel { etag = etag == null ? "" : etag; }
}
