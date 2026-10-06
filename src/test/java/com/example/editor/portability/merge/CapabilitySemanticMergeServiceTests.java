package com.example.editor.portability.merge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.example.editor.portability.HydraCapabilityPackage;
import com.example.editor.portability.HydraCapabilityPackageManifest;
import com.example.editor.portability.MetamodelOrigin;
import com.example.editor.portability.MetamodelOriginKind;
import com.example.editor.portability.transaction.CapabilityLayerTransactionService;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class CapabilitySemanticMergeServiceTests {
    @Test
    void pairsDifferentIdentifiersBySemanticSignatureAndKeepsTheMergeApplicable() throws Exception {
        var mapper = JsonMapper.builder().findAndAddModules().build();
        CapabilitySemanticMergeService service = new CapabilitySemanticMergeService(
                mock(CapabilityLayerTransactionService.class), mapper);
        HydraCapabilityPackage existing = capabilityPackage(mapper.readTree("""
                {"rules":[{"id":"existing-rule","name":"NoEmptyName","expression":"self.name <> ''","quickFixId":"fix-name"}],"quickFixes":[{"id":"fix-name","name":"SetName"}]}
                """));
        HydraCapabilityPackage recovered = capabilityPackage(mapper.readTree("""
                {"rules":[{"id":"recovered-rule","name":"NoEmptyName","expression":"self.name <> ''","quickFixId":"fix-name"}],"quickFixes":[{"id":"fix-name","name":"SetName"}]}
                """));

        CapabilitySemanticMergePreview preview = service.preview(new CapabilitySemanticMergeRequest(
                existing, recovered, "library", Map.of(), List.of(), CapabilitySemanticMergeDecision.MERGE,
                Map.of(), false));

        assertThat(preview.applicable()).isTrue();
        assertThat(preview.items()).anyMatch(item -> item.explanation().contains("different identifier"));
        assertThat(preview.conflicts()).isNotEmpty();
        assertThat(preview.dependencies()).allMatch(CapabilitySemanticMergeDependency::resolved);
        assertThat(preview.mergedPackage().canonicalDocuments().get("validations.json").toString())
                .contains("NoEmptyName", "existing-rule");
    }

    private HydraCapabilityPackage capabilityPackage(JsonNode validations) {
        MetamodelOrigin origin = new MetamodelOrigin(MetamodelOriginKind.OWNED, null, "", "", "", "", "",
                Map.of(), Map.of());
        HydraCapabilityPackageManifest manifest = new HydraCapabilityPackageManifest("1.0", "example.package",
                "Example", "1.0.0", "library", origin, List.of(), List.of(), List.of(), Map.of(), Map.of());
        return new HydraCapabilityPackage(manifest, Map.of("validations.json", validations), Map.of());
    }
}
