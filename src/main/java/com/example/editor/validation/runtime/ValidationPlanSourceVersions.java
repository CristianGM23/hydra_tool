package com.example.editor.validation.runtime;

/** ETags usados para decidir si un plan compilado sigue siendo vigente. */
public record ValidationPlanSourceVersions(String metamodelEtag, String expressionModelEtag, String actionModelEtag, String validationModelEtag, String operationBehaviorEtag) {
    public ValidationPlanSourceVersions {
        metamodelEtag = safe(metamodelEtag);
        expressionModelEtag = safe(expressionModelEtag);
        actionModelEtag = safe(actionModelEtag);
        validationModelEtag = safe(validationModelEtag);
        operationBehaviorEtag = safe(operationBehaviorEtag);
    }
    /** Descripción: Ejecuta la operación «safe» correspondiente a la responsabilidad de ValidationPlanSourceVersions. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String safe(String value) { return value == null ? "" : value; }
}
