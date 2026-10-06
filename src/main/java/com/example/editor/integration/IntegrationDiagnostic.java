package com.example.editor.integration;
/** Problema encontrado al comprobar un perfil de integración. */
public record IntegrationDiagnostic(IntegrationDiagnosticSeverity severity, String code, String elementId, String path, String message) {
    public IntegrationDiagnostic {
        if(severity == null) throw new IllegalArgumentException("severity is required");
        code = normalize(code); elementId = normalize(elementId); path = normalize(path); message = normalize(message);
    }
    /** Descripción: Ejecuta la operación «normalize» correspondiente a la responsabilidad de IntegrationDiagnostic. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String normalize(String value) { return value == null ? "" : value.trim(); }
}
