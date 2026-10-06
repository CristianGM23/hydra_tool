package com.example.editor.metamodel.export.project;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.example.editor.integration.IntegrationArtifactKind;
import com.example.editor.integration.projection.CanonicalArtifactContent;
import com.example.editor.metamodel.validation.MetamodelValidationGateStatus;
import com.example.editor.metamodel.validation.MetamodelValidationSummary;

class MetamodelProjectGenerationSnapshotTests {
    @Test void fingerprintCoversEveryArtifactAndSettingAndIsOrderIndependent(){
        var service = new MetamodelProjectGenerationSnapshotService(); var status = status("meta-sha");
        var metamodel = artifact(IntegrationArtifactKind.METAMODEL, "people", "meta");
        var a = artifact(IntegrationArtifactKind.VALIDATIONS, "a", "one"); var b = artifact(IntegrationArtifactKind.VALIDATIONS, "b", "two");
        var options = new MetamodelProjectExportOptions(true, MetamodelProjectArtifactSelectionMode.ALL_COMPATIBLE, List.of(), "", Map.of("serverLanguage", "java"));
        var first = service.create("people", MetamodelProjectExportKind.XTEXT, options, List.of(metamodel, a, b), status);
        var reordered = service.create("people", MetamodelProjectExportKind.XTEXT, options, List.of(b, metamodel, a), status);
        var changedLayer = service.create("people", MetamodelProjectExportKind.XTEXT, options, List.of(metamodel, a, artifact(IntegrationArtifactKind.VALIDATIONS, "b", "changed")), status);
        var changedSetting = service.create("people", MetamodelProjectExportKind.XTEXT,
                new MetamodelProjectExportOptions(true, MetamodelProjectArtifactSelectionMode.ALL_COMPATIBLE, List.of(), "", Map.of("serverLanguage", "typescript")), List.of(metamodel, a, b), status);
        assertThat(reordered.fingerprint()).isEqualTo(first.fingerprint());
        assertThat(changedLayer.fingerprint()).isNotEqualTo(first.fingerprint());
        assertThat(changedSetting.fingerprint()).isNotEqualTo(first.fingerprint());
        assertThat(first.artifacts()).hasSize(3).allMatch(item -> item.sha256().matches("[0-9a-f]{64}"));
    }
    private CanonicalArtifactContent artifact(IntegrationArtifactKind kind, String id, String content){return new CanonicalArtifactContent(kind, id, "application/json", content.getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    private MetamodelValidationGateStatus status(String fingerprint){return new MetamodelValidationGateStatus("people", true, true, true, false, true, true, fingerprint, fingerprint, Instant.now(), new MetamodelValidationSummary(0,0,0), "ready");}
}
