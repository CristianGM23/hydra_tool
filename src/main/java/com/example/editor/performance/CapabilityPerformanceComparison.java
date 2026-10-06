package com.example.editor.performance;

import java.util.List;
import java.util.Map;

/** Regression analysis between two AL capability measurements. */
public record CapabilityPerformanceComparison(String baselineReportId, String candidateReportId,
        String capabilityId, long renderDelta, long heapDelta, int requestDelta,
        long responseDelta, long analysisDelta, Map<String, Long> stageDeltas,
        Map<String, Long> chunkDeltas, boolean regression, List<String> diagnostics) {
    public CapabilityPerformanceComparison {
        stageDeltas = stageDeltas == null ? Map.of() : Map.copyOf(stageDeltas);
        chunkDeltas = chunkDeltas == null ? Map.of() : Map.copyOf(chunkDeltas);
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }
}
