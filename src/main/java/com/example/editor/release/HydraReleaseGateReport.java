package com.example.editor.release;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Immutable release gate report and attestation used by verified publication. */
public record HydraReleaseGateReport(String reportId, String productId, String version,
        Instant createdAt, boolean passed, String packageFingerprint, String mirrorId,
        List<String> adapterBenchReportIds, String performanceBaselineReportId,
        String performanceCandidateReportId, List<HydraReleaseGateCheck> checks,
        String attestationSha256, Map<String, String> annotations) {
    public HydraReleaseGateReport {
        createdAt = createdAt == null ? Instant.now() : createdAt;
        adapterBenchReportIds = adapterBenchReportIds == null ? List.of() : List.copyOf(adapterBenchReportIds);
        checks = checks == null ? List.of() : List.copyOf(checks);
        annotations = annotations == null ? Map.of() : Map.copyOf(annotations);
    }
}
