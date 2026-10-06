package com.example.editor.validation.validation;
/** Problema estático encontrado en una regla o documento de validaciones. */
public record ValidationModelDiagnostic(ValidationModelDiagnosticSeverity severity, String code, String ruleId, String path, String message) {
    public ValidationModelDiagnostic {
        if(severity == null) severity = ValidationModelDiagnosticSeverity.ERROR;
        code = code == null ? "" : code;
        ruleId = ruleId == null ? "" : ruleId;
        path = path == null ? "" : path;
        message = message == null ? "" : message;
    }
}
