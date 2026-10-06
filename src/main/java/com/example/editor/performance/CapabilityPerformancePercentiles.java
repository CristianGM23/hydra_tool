package com.example.editor.performance;

import java.util.Map;

/** Median and p95 values for one repeatable capability scenario. */
public record CapabilityPerformancePercentiles(long renderP50, long renderP95,
        long heapP50, long heapP95, int requestsP50, int requestsP95,
        long responseP50, long responseP95, long analysisP50, long analysisP95,
        Map<String, Long> stageP95, Map<String, Long> chunkP95) {
    public CapabilityPerformancePercentiles {
        stageP95 = stageP95 == null ? Map.of() : Map.copyOf(stageP95);
        chunkP95 = chunkP95 == null ? Map.of() : Map.copyOf(chunkP95);
    }
}
