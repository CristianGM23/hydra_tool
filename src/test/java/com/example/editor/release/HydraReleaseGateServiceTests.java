package com.example.editor.release;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.example.editor.metamodel.importing.updatesite.UpdateSiteAdapterBenchService;
import com.example.editor.metamodel.importing.updatesite.UpdateSiteP2MirrorService;
import com.example.editor.performance.CapabilityPerformanceService;
import com.example.editor.portability.CapabilityPackageCheckReport;
import com.example.editor.portability.CapabilityPackageChecker;
import com.example.editor.portability.HydraCapabilityPackage;
import com.example.editor.portability.HydraCapabilityPackageManifest;
import com.example.editor.portability.MetamodelOrigin;
import com.example.editor.portability.MetamodelOriginKind;
import tools.jackson.databind.json.JsonMapper;

class HydraReleaseGateServiceTests {
    @TempDir Path temporary;

    @Test
    void persistsAnAttestedPassAndBlocksMissingRequiredEvidence() throws Exception {
        CapabilityPackageChecker checker = mock(CapabilityPackageChecker.class);
        when(checker.check(any())).thenReturn(new CapabilityPackageCheckReport("example.package", List.of()));
        HydraReleaseGateService service = new HydraReleaseGateService(checker,
                mock(UpdateSiteP2MirrorService.class), mock(UpdateSiteAdapterBenchService.class),
                mock(CapabilityPerformanceService.class), JsonMapper.builder().findAndAddModules().build(),
                temporary.toString(), 200);
        HydraCapabilityPackage capabilityPackage = capabilityPackage();

        HydraReleaseGateReport optional = service.evaluate(new HydraReleaseGateRequest("hydra.example", "1.0.0",
                capabilityPackage, null, "", List.of(), "", "", false, false, false, false, Map.of()));
        HydraReleaseGateReport requiredMirror = service.evaluate(new HydraReleaseGateRequest("hydra.example", "1.0.1",
                capabilityPackage, null, "", List.of(), "", "", false, true, false, false, Map.of()));

        assertThat(optional.passed()).isTrue();
        assertThat(service.assertPassed(optional.reportId()).attestationSha256()).isEqualTo(optional.attestationSha256());
        assertThat(optional.attestationSha256()).hasSize(64);
        assertThat(requiredMirror.passed()).isFalse();
        assertThat(requiredMirror.checks()).anyMatch(check -> check.code().equals("P2_MIRROR")
                && check.status() == HydraReleaseGateStatus.FAIL);
        assertThat(service.reports()).extracting(HydraReleaseGateReport::reportId)
                .contains(optional.reportId(), requiredMirror.reportId());
        Path stored = temporary.resolve(optional.reportId() + ".json");
        java.nio.file.Files.writeString(stored, java.nio.file.Files.readString(stored)
                .replace("hydra.example", "hydra.tampered"));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.find(optional.reportId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("attestation");
        org.assertj.core.api.Assertions.assertThatThrownBy(service::reports)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Corrupt or unreadable release gate report");
    }

    private HydraCapabilityPackage capabilityPackage() {
        MetamodelOrigin origin = new MetamodelOrigin(MetamodelOriginKind.OWNED, null, "", "", "", "", "",
                Map.of(), Map.of());
        HydraCapabilityPackageManifest manifest = new HydraCapabilityPackageManifest("1.0", "example.package",
                "Example package", "1.0.0", "example", origin, List.of(), List.of(), List.of(), Map.of(), Map.of());
        return new HydraCapabilityPackage(manifest, Map.of(), Map.of());
    }
}
