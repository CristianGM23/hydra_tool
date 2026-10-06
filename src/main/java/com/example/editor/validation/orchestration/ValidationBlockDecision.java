package com.example.editor.validation.orchestration;

import java.util.List;

/** Resultado de aplicar una política de bloqueo sin modificar la severidad del diagnóstico. */
public record ValidationBlockDecision(ValidationGate gate, boolean blocked, List<String> diagnosticIds, String reason) {
    public ValidationBlockDecision {
        if(gate == null) gate = ValidationGate.NONE;
        diagnosticIds = diagnosticIds == null ? List.of() : List.copyOf(diagnosticIds);
        reason = reason == null ? "" : reason;
    }
    /** Descripción: Ejecuta la operación «allowed» correspondiente a la responsabilidad de ValidationBlockDecision. Entradas: gate: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static ValidationBlockDecision allowed(ValidationGate gate) { return new ValidationBlockDecision(gate, false, List.of(), ""); }
}
