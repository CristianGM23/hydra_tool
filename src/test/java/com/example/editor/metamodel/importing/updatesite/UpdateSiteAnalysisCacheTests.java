package com.example.editor.metamodel.importing.updatesite;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UpdateSiteAnalysisCacheTests {
    @Test
    void keepsAnLruBoundWithoutChangingTheAnalysisContract() {
        UpdateSiteImportProperties properties = new UpdateSiteImportProperties();
        properties.setAnalysisCacheEntries(2);
        UpdateSiteAnalysisCache cache = new UpdateSiteAnalysisCache(properties);
        UpdateSiteImportAnalysis first = analysis("first");
        UpdateSiteImportAnalysis second = analysis("second");
        UpdateSiteImportAnalysis third = analysis("third");

        cache.put("first", first);
        cache.put("second", second);
        assertThat(cache.find("first")).contains(first);
        cache.put("third", third);

        assertThat(cache.find("second")).isEmpty();
        assertThat(cache.find("first")).contains(first);
        assertThat(cache.find("third")).contains(third);
        assertThat(cache.metrics().entries()).isEqualTo(2);
        assertThat(cache.metrics().writes()).isEqualTo(3);
    }

    @Test
    void canBeDisabledWithoutReturningStaleData() {
        UpdateSiteImportProperties properties = new UpdateSiteImportProperties();
        properties.setAnalysisCacheEntries(0);
        UpdateSiteAnalysisCache cache = new UpdateSiteAnalysisCache(properties);

        cache.put("analysis", analysis("analysis"));

        assertThat(cache.find("analysis")).isEmpty();
        assertThat(cache.metrics().entries()).isZero();
    }

    private UpdateSiteImportAnalysis analysis(String id) {
        return new UpdateSiteImportAnalysis(id, Instant.now(), new UpdateSiteRepositorySummary("site", "fingerprint", true, true, 0, 0,
                        List.of(), List.of(), List.of(), List.of(), List.of(), List.of()), List.of(), "", null, List.of(), UpdateSiteRecoveredLayers.empty(), false, List.of(), UpdateSiteImportDashboard.empty(), List.of(), List.of(), List.of(), UpdateSiteReconciliationReport.empty(), UpdateSiteReleaseComparison.empty(), UpdateSiteSemanticRecovery.empty(), UpdateSiteAnalysisPerformance.empty());
    }
}
