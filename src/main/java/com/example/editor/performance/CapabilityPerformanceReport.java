package com.example.editor.performance;

/** Comparable persisted performance report. */
public record CapabilityPerformanceReport(String reportId, CapabilityPerformanceBudget budget,
        CapabilityPerformanceSample sample, CapabilityPerformanceEvaluation evaluation) { }
