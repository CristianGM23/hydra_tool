package com.example.editor.policy.bundle;

import java.time.Instant;
import java.util.List;
import com.example.editor.policy.HydraPolicyEvaluationReport;

/** Explainable BD simulation evidence. */
public record HydraPolicyBundleSimulationReport(String reportId, String bundleId, String version,
        boolean allowed, List<HydraPolicyEvaluationReport> evaluations, List<String> diagnostics,
        String bundleAttestationSha256, Instant simulatedAt) {
    public HydraPolicyBundleSimulationReport { evaluations=evaluations==null?List.of():List.copyOf(evaluations);diagnostics=diagnostics==null?List.of():List.copyOf(diagnostics);simulatedAt=simulatedAt==null?Instant.now():simulatedAt; }
}
