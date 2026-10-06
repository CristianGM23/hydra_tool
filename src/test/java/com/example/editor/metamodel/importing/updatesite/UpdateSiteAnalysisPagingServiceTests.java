package com.example.editor.metamodel.importing.updatesite;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class UpdateSiteAnalysisPagingServiceTests {
    @Test
    void returnsBoundedFilteredPagesWithoutRecomputingTheAnalysis() {
        UpdateSiteImportProperties properties = new UpdateSiteImportProperties();
        properties.setAnalysisCacheEntries(2);
        UpdateSiteAnalysisCache cache = new UpdateSiteAnalysisCache(properties);
        UpdateSiteImportAnalysis analysis = new UpdateSiteImportAnalysis("analysis-1", Instant.now(), null, List.of(), "", null, List.of(
                    provenance("book", "//Book", UpdateSiteConfidence.EXACT),
                    provenance("author", "//Author", UpdateSiteConfidence.INFERRED),
                    provenance("library", "//Library", UpdateSiteConfidence.EXACT)), UpdateSiteRecoveredLayers.empty(), true, List.of(), UpdateSiteImportDashboard.empty(), List.of(), List.of(), List.of(), UpdateSiteReconciliationReport.empty(), UpdateSiteReleaseComparison.empty(), UpdateSiteSemanticRecovery.empty(), UpdateSiteAnalysisPerformance.empty());
        cache.put("request-fingerprint", analysis);
        UpdateSiteAnalysisPagingService service = new UpdateSiteAnalysisPagingService(cache,
                JsonMapper.builder().findAndAddModules().build());

        UpdateSiteAnalysisPage<UpdateSiteElementProvenance> first = service.evidence(
                "analysis-1", 0, 1, "//", "EXACT");
        UpdateSiteAnalysisPage<UpdateSiteElementProvenance> second = service.evidence(
                "analysis-1", 1, 1, "//", "EXACT");
        UpdateSiteCompactAnalysis compact = service.compact("analysis-1");

        assertThat(first.totalElements()).isEqualTo(2);
        assertThat(first.totalPages()).isEqualTo(2);
        assertThat(first.items()).singleElement().extracting(UpdateSiteElementProvenance::elementId)
                .isEqualTo("book");
        assertThat(second.items()).singleElement().extracting(UpdateSiteElementProvenance::elementId)
                .isEqualTo("library");
        assertThat(compact.collectionSizes()).containsEntry("evidence", 3);
        assertThat(compact.analysis().provenance()).isEmpty();
    }

    private UpdateSiteElementProvenance provenance(String id, String path, UpdateSiteConfidence confidence) {
        return new UpdateSiteElementProvenance(id, path, "bundle", "model.ecore", "ECORE", path,
                UpdateSiteRecoveryMethod.DIRECT_METAMODEL, confidence, List.of());
    }
}
