package com.example.editor.metamodel.importing.updatesite;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UpdateSiteMetamodelReconciliationServiceTests {
    private final UpdateSiteMetamodelRecoveryService recovery = new UpdateSiteMetamodelRecoveryService(null,
            new UpdateSiteSchemaMetamodelDeriver(JsonMapper.builder().build()), new ClassFileStructureReader());
    private final UpdateSiteMetamodelReconciliationService service = new UpdateSiteMetamodelReconciliationService(
            recovery, new UpdateSiteImportProperties());

    @Test
    void requiresExplicitDecisionsForConflictingRecoveredStructuresAndMergesThemDeterministically() {
        byte[] first = "{\"title\":\"A\",\"name\":\"Library\"}".getBytes(StandardCharsets.UTF_8);
        byte[] second = "{\"title\":\"B\",\"code\":42}".getBytes(StandardCharsets.UTF_8);
        UpdateSiteRepositoryContent repository = new UpdateSiteRepositoryContent(Map.of(
                "a/library.json", first,
                "b/library.json", second), "f", first.length + second.length);
        UpdateSiteRecoveryCandidate candidateA = candidate("a", "a/library.json", 90);
        UpdateSiteRecoveryCandidate candidateB = candidate("b", "b/library.json", 80);

        UpdateSiteMetamodelReconciliationService.ReconciliationState state = service.analyze(
                List.of(candidateA, candidateB), repository, "library");

        assertThat(state.report().candidates()).hasSize(2);
        assertThat(state.report().conflicts()).anyMatch(value -> value.elementKey().equals("CLASS:Library"));
        assertThat(state.report().mergeReady()).isFalse();
        assertThatThrownBy(() -> service.merge(state, new UpdateSiteReconciliationRequest("library", "a", Map.of())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("decision");

        Map<String, String> decisions = state.report().conflicts().stream()
                .collect(java.util.stream.Collectors.toMap(UpdateSiteReconciliationConflict::elementKey, ignored -> "a"));
        var merged = service.merge(state, new UpdateSiteReconciliationRequest("library", "a", decisions));

        assertThat(merged.annotations()).containsEntry("hydra.reconciliation.primaryCandidate", "a");
        assertThat(merged.classes()).filteredOn(value -> value.name().equals("Library")).singleElement()
                .satisfies(value -> assertThat(value.attributes()).extracting(item -> item.name()).contains("title", "name"));
    }

    @Test
    void autoUnionsComplementaryClassFeaturesWhenCandidatesDoNotDisagree() {
        byte[] first = "{\"title\":\"Example\",\"name\":\"Library\"}".getBytes(StandardCharsets.UTF_8);
        byte[] second = "{\"title\":\"Example\",\"code\":42}".getBytes(StandardCharsets.UTF_8);
        UpdateSiteRepositoryContent repository = new UpdateSiteRepositoryContent(Map.of(
                "a/library.json", first, "b/library.json", second), "f", first.length + second.length);
        UpdateSiteRecoveryCandidate candidateA = candidate("a", "a/library.json", 90);
        UpdateSiteRecoveryCandidate candidateB = candidate("b", "b/library.json", 80);

        UpdateSiteMetamodelReconciliationService.ReconciliationState state = service.analyze(
                List.of(candidateA, candidateB), repository, "library");

        assertThat(state.report().conflicts()).filteredOn(value -> value.elementKey().equals("CLASS:Library")).singleElement()
                .satisfies(value -> {
                    assertThat(value.blocking()).isFalse();
                    assertThat(value.explanation()).contains("complementary");
                });
        var merged = service.merge(state, new UpdateSiteReconciliationRequest("library", "a", Map.of()));
        assertThat(merged.classes()).filteredOn(value -> value.name().equals("Library")).singleElement()
                .satisfies(value -> assertThat(value.attributes()).extracting(item -> item.name()).contains("title", "name", "code"));
    }

    private UpdateSiteRecoveryCandidate candidate(String id, String path, int score) {
        return new UpdateSiteRecoveryCandidate(id, 1, UpdateSiteRecoveryMethod.DIRECT_METAMODEL,
                UpdateSiteConfidence.DERIVED, path, "", "JSON_DATA", score, true, "test", List.of());
    }
}
