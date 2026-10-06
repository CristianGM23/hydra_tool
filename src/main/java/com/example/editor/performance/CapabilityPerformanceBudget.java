package com.example.editor.performance;

/** Guardrails for one independently loaded HYDRA capability. */
public record CapabilityPerformanceBudget(String capabilityId, long maxRenderMillis,
        long maxHeapBytes, int maxRequests, long maxResponseBytes, long maxAnalysisMillis) {
    public CapabilityPerformanceBudget {
        capabilityId = capabilityId == null ? "" : capabilityId.trim();
        if(capabilityId.isBlank()) throw new IllegalArgumentException("capabilityId is required");
        maxRenderMillis = Math.max(1, maxRenderMillis); maxHeapBytes = Math.max(1, maxHeapBytes);
        maxRequests = Math.max(1, maxRequests); maxResponseBytes = Math.max(1, maxResponseBytes);
        maxAnalysisMillis = Math.max(1, maxAnalysisMillis);
    }
}
