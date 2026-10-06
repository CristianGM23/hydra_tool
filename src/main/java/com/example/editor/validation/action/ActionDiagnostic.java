package com.example.editor.validation.action;
/** Problema estático encontrado en una acción. */
public record ActionDiagnostic(ActionDiagnosticSeverity severity, String code, String actionId, String operationId, String path, String message) {
    public ActionDiagnostic {
        if(severity == null) severity = ActionDiagnosticSeverity.ERROR;
        code = code == null ? "" : code;
        actionId = actionId == null ? "" : actionId;
        operationId = operationId == null ? "" : operationId;
        path = path == null ? "" : path;
        message = message == null ? "" : message;
    }
}
