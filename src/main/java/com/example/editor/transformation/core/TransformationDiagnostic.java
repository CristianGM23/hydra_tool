package com.example.editor.transformation.core;

public record TransformationDiagnostic(
        TransformationDiagnosticSeverity severity,
        String code,
        String ruleId,
        String bindingId,
        String path,
        String title,
        String message,
        String solution) {

    public TransformationDiagnostic {
        severity = severity == null ? TransformationDiagnosticSeverity.ERROR : severity;
        code = text(code);
        ruleId = text(ruleId);
        bindingId = text(bindingId);
        path = text(path);
        title = text(title);
        message = text(message);
        solution = text(solution);
    }

    /** Descripción: Ejecuta la operación «text» correspondiente a la responsabilidad de TransformationDiagnostic. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String text(String value) {
        return value == null ? "" : value;
    }
}
