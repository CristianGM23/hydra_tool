package com.example.editor.metamodel.importing.updatesite;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UpdateSiteImportObservabilityServiceTests {
    private final UpdateSiteImportProperties properties = new UpdateSiteImportProperties();
    private final UpdateSiteImportObservabilityService service = new UpdateSiteImportObservabilityService(properties,
            new UpdateSiteGrammarInspectionService());

    @Test
    void buildsCompactDashboardAndExplainsFilesThatCannotBecomeNeutralLayers() {
        Map<String, byte[]> files = Map.of(
                "plugins/library.jar!/model/library.ecore", "<ecore:EPackage/>".getBytes(StandardCharsets.UTF_8),
                "plugins/library.jar!/language/Library.xtext", "grammar Library\nModel: names+=ID*;".getBytes(StandardCharsets.UTF_8),
                "plugins/library.jar!/icons/book.png", new byte[]{1, 2, 3},
                "plugins/library.jar!/runtime/custom.dat", new byte[]{9, 8});
        UpdateSiteRepositoryContent repository = new UpdateSiteRepositoryContent(files, "f", 100);
        UpdateSiteRepositorySummary summary = new UpdateSiteRepositorySummary("Library site", "f", false, false,
                files.size(), 100, List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        UpdateSiteRecoveryCandidate candidate = new UpdateSiteRecoveryCandidate("ecore", 1,
                UpdateSiteRecoveryMethod.DIRECT_METAMODEL, UpdateSiteConfidence.EXACT,
                "plugins/library.jar!/model/library.ecore", "library", "ECORE", 100, true, "original", List.of());
        UpdateSiteElementProvenance provenance = new UpdateSiteElementProvenance("class:Book", "//Book", "library.jar",
                candidate.sourcePath(), "Ecore", "//Book", UpdateSiteRecoveryMethod.DIRECT_METAMODEL,
                UpdateSiteConfidence.EXACT, List.of());

        UpdateSiteObservability result = service.build(repository, summary, List.of(candidate), candidate,
                List.of(provenance), UpdateSiteRecoveredLayers.empty(), UpdateSiteSemanticRecovery.empty(),
                UpdateSiteReconciliationReport.empty(), UpdateSiteReleaseComparison.empty(), List.of());

        assertThat(result.dashboard().inspectedFiles()).isEqualTo(4);
        assertThat(result.dashboard().flowNodes()).isNotEmpty();
        assertThat(result.dashboard().flowLinks()).isNotEmpty();
        assertThat(result.grammarInspections()).singleElement().satisfies(grammar -> {
            assertThat(grammar.language()).isEqualTo("Xtext");
            assertThat(grammar.assignments()).isGreaterThan(0);
        });
        assertThat(result.fileOutcomes()).filteredOn(value -> value.sourcePath().endsWith("book.png"))
                .singleElement().satisfies(outcome -> {
                    assertThat(outcome.purpose()).isNotBlank();
                    assertThat(outcome.explanation()).isNotBlank();
                });
        assertThat(result.fileOutcomes()).filteredOn(value -> value.sourcePath().endsWith("custom.dat"))
                .singleElement().satisfies(outcome -> {
                    assertThat(outcome.status()).isEqualTo(UpdateSiteFileStatus.NOT_RECOVERABLE);
                    assertThat(outcome.nextAction()).isNotBlank();
                });
        assertThat(result.logs()).anyMatch(log -> log.stage() == UpdateSiteImportStage.GRAMMAR_ANALYSIS);
    }
}
