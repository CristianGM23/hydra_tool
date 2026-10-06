package com.example.editor.semantic.expression;

/** Problema estructural o de tipado detectado en una expresión. */
public record ExpressionDiagnostic(ExpressionDiagnosticSeverity severity, String code, String expressionId, String nodeId, String path, String message) {
    public ExpressionDiagnostic {
        if(severity == null) severity = ExpressionDiagnosticSeverity.ERROR;
        code = code == null ? "UNKNOWN" : code;
        expressionId = expressionId == null ? "" : expressionId;
        nodeId = nodeId == null ? "" : nodeId;
        path = path == null ? "" : path;
        message = message == null ? "" : message;
    }
}
