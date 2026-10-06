package com.example.editor.validation.orchestration;

import com.example.editor.validation.runtime.ModelValidationReport;

/** Puerto que decide si una operación queda bloqueada por un informe. */
public interface ValidationBlockingPolicy {
    /** Descripción: Ejecuta la operación «evaluate» correspondiente a la responsabilidad de ValidationBlockingPolicy. Entradas: report: dato de entrada necesario para la operación; gate: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ ValidationBlockDecision evaluate(ModelValidationReport report, ValidationGate gate);
}
