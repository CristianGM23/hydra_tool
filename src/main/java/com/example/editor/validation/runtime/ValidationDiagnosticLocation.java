package com.example.editor.validation.runtime;
/** Ubicación neutral de un diagnóstico sobre el modelo. */
public record ValidationDiagnosticLocation(String modelElementId, String featureId, String relationInstanceId) {
    public ValidationDiagnosticLocation {
        modelElementId = safe(modelElementId); featureId = safe(featureId); relationInstanceId = safe(relationInstanceId);
    }
    /** Descripción: Ejecuta la operación «safe» correspondiente a la responsabilidad de ValidationDiagnosticLocation. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String safe(String value) { return value == null ? "" : value; }
}
