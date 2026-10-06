package com.example.editor.performance;

import java.time.Instant;

/** Approved scenario baseline used by AQ and future AU comparisons. */
public record CapabilityPerformanceBaseline(String baselineId, String scenarioId,
        String capabilityId, Instant approvedAt, String automationReportId,
        String performanceReportId, CapabilityPerformancePercentiles percentiles) {
    public CapabilityPerformanceBaseline {
        approvedAt = approvedAt == null ? Instant.now() : approvedAt;
    }

    /** Backward-compatible alias for the AU report that produced this baseline. */
    /** Descripción: Ejecuta la operación «sourceReportId» correspondiente a la responsabilidad de CapabilityPerformanceBaseline. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public String sourceReportId() {
        return automationReportId;
    }
}
