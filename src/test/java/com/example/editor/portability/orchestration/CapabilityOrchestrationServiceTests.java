package com.example.editor.portability.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.example.editor.portability.HydraCapabilityPackage;
import com.example.editor.portability.HydraCapabilityPackageManifest;
import com.example.editor.portability.MetamodelOrigin;
import com.example.editor.portability.MetamodelOriginKind;
import com.example.editor.portability.merge.CapabilitySemanticMergeDecision;
import com.example.editor.portability.merge.CapabilitySemanticMergeService;
import com.example.editor.portability.transaction.CapabilityLayerTransactionService;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class CapabilityOrchestrationServiceTests {
    @Test
    void ordersMultipleSourcesAndReplaysTheSameDeterministicPlan() throws Exception {
        var mapper = JsonMapper.builder().findAndAddModules().build();
        var transactions = mock(CapabilityLayerTransactionService.class);
        var service = new CapabilityOrchestrationService(
                new CapabilitySemanticMergeService(transactions, mapper), transactions, mapper);
        CapabilityOrchestrationRequest request = new CapabilityOrchestrationRequest(
                capabilityPackage(mapper.readTree("{\"rules\":[]}")), List.of(
                source("second", 20, List.of("first"), capabilityPackage(mapper.readTree("{\"rules\":[{\"id\":\"b\",\"name\":\"B\"}]}"))),
                source("first", 10, List.of(), capabilityPackage(mapper.readTree("{\"rules\":[{\"id\":\"a\",\"name\":\"A\"}]}")))),
                "library", Map.of(), List.of(), false);

        CapabilityOrchestrationPreview preview = service.preview(request);
        CapabilityOrchestrationPreview replay = service.replay(request, preview.orchestrationId());

        assertThat(preview.applicable()).isTrue();
        assertThat(preview.precedenceOrder()).containsExactly("first", "second");
        assertThat(preview.steps()).extracting(CapabilityOrchestrationStep::sourceId).containsExactly("first", "second");
        assertThat(preview.steps()).allMatch(step -> !step.policyFingerprint().isBlank());
        assertThat(replay.orchestrationId()).isEqualTo(preview.orchestrationId());
        assertThat(preview.mergedPackage().canonicalDocuments().get("validations.json").toString()).contains("A", "B");
    }

    @Test
    void rejectsCyclesInTheSourcePrecedenceGraph() throws Exception {
        var mapper = JsonMapper.builder().findAndAddModules().build();
        var transactions = mock(CapabilityLayerTransactionService.class);
        var service = new CapabilityOrchestrationService(new CapabilitySemanticMergeService(transactions, mapper), transactions, mapper);
        HydraCapabilityPackage pkg = capabilityPackage(mapper.readTree("{\"rules\":[]}"));
        var request = new CapabilityOrchestrationRequest(pkg, List.of(
                source("a", 1, List.of("b"), pkg), source("b", 2, List.of("a"), pkg)), "library", Map.of(), List.of(), false);

        assertThatThrownBy(() -> service.preview(request)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("cycle");
    }

    private CapabilityOrchestrationSource source(String id, int precedence, List<String> after, HydraCapabilityPackage pkg) {
        return new CapabilityOrchestrationSource(id, precedence, after, pkg, CapabilitySemanticMergeDecision.MERGE, Map.of());
    }

    private HydraCapabilityPackage capabilityPackage(JsonNode validations) {
        MetamodelOrigin origin = new MetamodelOrigin(MetamodelOriginKind.OWNED, null, "", "", "", "", "", Map.of(), Map.of());
        HydraCapabilityPackageManifest manifest = new HydraCapabilityPackageManifest("1.0", "example.package", "Example", "1.0.0",
                "library", origin, List.of(), List.of(), List.of(), Map.of(), Map.of());
        return new HydraCapabilityPackage(manifest, Map.of("validations.json", validations), Map.of());
    }
}
