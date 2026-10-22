package com.example.editor.portability.merge;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.example.editor.portability.HydraCapabilityPackage;
import com.example.editor.portability.HydraCapabilityPackageManifest;
import com.example.editor.portability.MetamodelOrigin;
import com.example.editor.portability.MetamodelOriginKind;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Regresión del defecto #11 de HALLAZGOS_PRODUCCION: los campos que son solo referencias (quickFixId, validationId,
 * actionId...) se registraban también como identidad, de modo que una referencia colgante se resolvía contra sí misma
 * y la fusión seguía siendo aplicable. Servicio de fusión real sobre paquetes reales; el oráculo son los literales JSON.
 */
class CapabilitySemanticMergeDanglingReferenceTests {
    private final tools.jackson.databind.ObjectMapper mapper = JsonMapper.builder().findAndAddModules().build();
    // La transacción de capas solo se usa al aplicar; la vista previa que se demuestra aquí no la toca.
    private final CapabilitySemanticMergeService service = new CapabilitySemanticMergeService(null, mapper);

    @Test
    void aDanglingQuickFixReferenceBlocksTheMerge() throws Exception {
        JsonNode document = mapper.readTree("""
                {"rules":[{"id":"rule-name","name":"NoEmptyName","expression":"self.name <> ''","quickFixId":"fix-ghost"}],
                 "quickFixes":[{"id":"fix-name","name":"SetName"}]}
                """);

        CapabilitySemanticMergePreview preview = merge(document);

        assertThat(preview.applicable()).isFalse();
        assertThat(preview.dependencies()).filteredOn(item -> !item.resolved())
                .extracting(CapabilitySemanticMergeDependency::targetReference).isNotEmpty().containsOnly("fix-ghost");
    }

    @Test
    void aDanglingValidationOrActionReferenceInsideAnObjectWithItsOwnIdIsDetected() throws Exception {
        JsonNode document = mapper.readTree("""
                {"bindings":[{"id":"binding-1","validationId":"validation-ghost","actionId":"action-ghost"}],
                 "validations":[{"id":"validation-real"}],"actions":[{"id":"action-real"}]}
                """);

        CapabilitySemanticMergePreview preview = merge(document);

        assertThat(preview.applicable()).isFalse();
        assertThat(preview.dependencies()).filteredOn(item -> !item.resolved())
                .extracting(CapabilitySemanticMergeDependency::targetReference)
                .containsOnly("validation-ghost", "action-ghost");
    }

    @Test
    void theSameReferenceIsResolvedWhenItsTargetIsDefinedByIdAndTheMergeStaysApplicable() throws Exception {
        JsonNode document = mapper.readTree("""
                {"rules":[{"id":"rule-name","name":"NoEmptyName","expression":"self.name <> ''","quickFixId":"fix-name"}],
                 "quickFixes":[{"id":"fix-name","name":"SetName"}]}
                """);

        CapabilitySemanticMergePreview preview = merge(document);

        assertThat(preview.applicable()).isTrue();
        assertThat(preview.dependencies()).isNotEmpty().allMatch(CapabilitySemanticMergeDependency::resolved);
    }

    private CapabilitySemanticMergePreview merge(JsonNode document) {
        return service.preview(new CapabilitySemanticMergeRequest(capabilityPackage(document), capabilityPackage(document),
                "library", Map.of(), List.of(), CapabilitySemanticMergeDecision.MERGE, Map.of(), false));
    }

    private HydraCapabilityPackage capabilityPackage(JsonNode validations) {
        MetamodelOrigin origin = new MetamodelOrigin(MetamodelOriginKind.OWNED, null, "", "", "", "", "", Map.of(), Map.of());
        HydraCapabilityPackageManifest manifest = new HydraCapabilityPackageManifest("1.0", "example.package",
                "Example", "1.0.0", "library", origin, List.of(), List.of(), List.of(), Map.of(), Map.of());
        return new HydraCapabilityPackage(manifest, Map.of("validations.json", validations), Map.of());
    }
}
