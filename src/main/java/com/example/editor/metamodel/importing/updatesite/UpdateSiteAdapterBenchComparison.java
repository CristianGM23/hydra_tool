package com.example.editor.metamodel.importing.updatesite;

import java.util.List;

/** Difference between two persisted adapter bench reports. */
public record UpdateSiteAdapterBenchComparison(String baselineReportId, String candidateReportId,
        int fixturePassDelta, int killedMutationDelta, int tokenCoverageDelta,
        int elementCoverageDelta, boolean regression, List<String> diagnostics) {
    public UpdateSiteAdapterBenchComparison {
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }
}
