package com.example.editor.metamodel.importing.updatesite;

import java.time.Instant;
import java.util.List;

/** Informe de verificación con fixtures generados, fuzzing e invariantes. */
public record UpdateSiteAdapterVerificationReport(String reportId, String adapterId,
        String adapterVersion, String adapterFingerprint, Instant createdAt,
        String benchReportId, int generatedFixtures, int fuzzCases,
        int invariantChecks, int failures,
        List<UpdateSiteAdapterVerificationFailure> failureDetails,
        boolean passed, List<String> diagnostics) {
    public UpdateSiteAdapterVerificationReport {
        createdAt = createdAt == null ? Instant.now() : createdAt;
        failureDetails = failureDetails == null ? List.of() : List.copyOf(failureDetails);
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }
}
