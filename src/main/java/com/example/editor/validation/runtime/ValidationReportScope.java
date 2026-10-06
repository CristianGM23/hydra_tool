package com.example.editor.validation.runtime;
/** Alcance resumido de una ejecución de validaciones. */
public record ValidationReportScope(ValidationReportScopeKind kind, String elementId, String featureId, String ruleId, String ruleSetId) {
    public ValidationReportScope {
        if(kind == null) kind = ValidationReportScopeKind.MODEL;
        elementId = safe(elementId); featureId = safe(featureId); ruleId = safe(ruleId); ruleSetId = safe(ruleSetId);
    }
    /** Descripción: Ejecuta la operación «model» correspondiente a la responsabilidad de ValidationReportScope. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public static ValidationReportScope model() { return new ValidationReportScope(ValidationReportScopeKind.MODEL, "", "", "", ""); }
    /** Descripción: Ejecuta la operación «safe» correspondiente a la responsabilidad de ValidationReportScope. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String safe(String value) { return value == null ? "" : value; }
}
