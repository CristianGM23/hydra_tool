package com.example.editor.metamodel.importing.updatesite;

import java.time.Instant;
import java.util.List;

/** Persisted JSON/HTML adapter bench report. */
public record UpdateSiteAdapterBenchReport(String reportId, String adapterId, String adapterVersion,
        String adapterFingerprint, Instant createdAt, int passedFixtures, int failedFixtures,
        int killedMutations, int survivingMutations, UpdateSiteAdapterCoverage coverage,
        List<UpdateSiteAdapterBenchCaseResult> fixtures,
        List<UpdateSiteAdapterMutationResult> mutations, List<String> diagnostics) {
    public UpdateSiteAdapterBenchReport {
        createdAt = createdAt == null ? Instant.now() : createdAt;
        fixtures = fixtures == null ? List.of() : List.copyOf(fixtures);
        mutations = mutations == null ? List.of() : List.copyOf(mutations);
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }
}
