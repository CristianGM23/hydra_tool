package com.example.editor.performance;

import java.util.List;

/** Repeatable AU scenario with warm-up, samples, noise tolerance and optional approval. */
public record CapabilityPerformanceScenarioRequest(String scenarioId, String capabilityId,
        int warmupCount, List<CapabilityPerformanceSample> samples,
        CapabilityPerformanceBudget budget, int allowedNoisePercent,
        boolean approveBaseline) {
    public CapabilityPerformanceScenarioRequest {
        scenarioId = text(scenarioId); capabilityId = text(capabilityId);
        warmupCount = Math.max(0, warmupCount);
        samples = samples == null ? List.of() : List.copyOf(samples);
        allowedNoisePercent = Math.max(0, Math.min(100, allowedNoisePercent));
    }
    /** Descripción: Ejecuta la operación «text» correspondiente a la responsabilidad de CapabilityPerformanceScenarioRequest. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String text(String value){return value == null ? "" : value.trim();}
}
