package com.example.editor.validation.runtime;

/** Diagnóstico estable generado por ValidationCompiler. */
public record ValidationCompilationDiagnostic(ValidationCompilationSeverity severity, String code, String ruleId, String message) {
    public ValidationCompilationDiagnostic {
        if(severity == null) severity = ValidationCompilationSeverity.ERROR;
        code = code == null ? "" : code;
        ruleId = ruleId == null ? "" : ruleId;
        message = message == null ? "" : message;
    }
}
