package com.example.editor.performance;

import java.time.Instant;
import java.util.List;

/** AU suite result with percentiles, noise analysis and baseline comparison. */
public record CapabilityPerformanceAutomationReport(String reportId, String scenarioId,
        String capabilityId, Instant createdAt, int warmupCount, int measuredSamples,
        int allowedNoisePercent, int observedNoisePercent, boolean noisy,
        CapabilityPerformancePercentiles percentiles, String performanceReportId,
        String baselineId, boolean regression, boolean passed,
        List<String> diagnostics) {
    public CapabilityPerformanceAutomationReport {
        createdAt = createdAt == null ? Instant.now() : createdAt;
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }
}
