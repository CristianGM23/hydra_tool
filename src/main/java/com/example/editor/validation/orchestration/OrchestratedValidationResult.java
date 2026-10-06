package com.example.editor.validation.orchestration;

import com.example.editor.validation.runtime.ModelValidationReport;

/** Informe consolidado, delta de problemas y decisión de bloqueo de una ejecución. */
public record OrchestratedValidationResult(ModelValidationReport report, ValidationReportDelta delta,
        ValidationBlockDecision blocking) {
    public OrchestratedValidationResult {
        if(report == null) throw new IllegalArgumentException("report must not be null");
        if(delta == null) delta = ValidationReportDelta.empty();
        if(blocking == null) blocking = ValidationBlockDecision.allowed(ValidationGate.NONE);
    }
}
