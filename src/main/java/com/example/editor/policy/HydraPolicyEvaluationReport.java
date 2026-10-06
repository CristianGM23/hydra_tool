package com.example.editor.policy;

import java.time.Instant;
import java.util.List;

/** Re-evaluation report for an existing publication. */
public record HydraPolicyEvaluationReport(String reportId, String provenanceReportId, boolean allowed,
        List<HydraPolicyDiagnostic> diagnostics, List<String> revokedSubjects, String policyFingerprint,
        Instant evaluatedAt) {
    public HydraPolicyEvaluationReport { diagnostics=diagnostics==null?List.of():List.copyOf(diagnostics);revokedSubjects=revokedSubjects==null?List.of():List.copyOf(revokedSubjects);evaluatedAt=evaluatedAt==null?Instant.now():evaluatedAt; }
}
