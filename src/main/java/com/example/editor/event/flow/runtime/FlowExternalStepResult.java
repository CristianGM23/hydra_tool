package com.example.editor.event.flow.runtime;

import java.util.Map;

public record FlowExternalStepResult(boolean successful, Map<String, Object> payloadUpdates, String message) {
    public FlowExternalStepResult {
        payloadUpdates = payloadUpdates == null ? Map.of() : Map.copyOf(payloadUpdates);
        message = message == null ? "" : message;
    }
    /** Descripción: Ejecuta la operación «success» correspondiente a la responsabilidad de FlowExternalStepResult. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public static FlowExternalStepResult success() { return new FlowExternalStepResult(true, Map.of(), ""); }
}
