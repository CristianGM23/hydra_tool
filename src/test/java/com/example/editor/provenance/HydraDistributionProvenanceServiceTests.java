package com.example.editor.provenance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.example.editor.metamodel.importing.updatesite.UpdateSiteAdapterVerificationService;
import com.example.editor.metamodel.importing.updatesite.UpdateSiteMirrorChannel;
import com.example.editor.metamodel.importing.updatesite.UpdateSiteMirrorUpdateService;
import com.example.editor.metamodel.importing.updatesite.UpdateSiteP2MirrorService;
import com.example.editor.performance.CapabilityPerformanceAutomationService;
import com.example.editor.portability.CapabilityPackageCheckReport;
import com.example.editor.portability.CapabilityPackageChecker;
import com.example.editor.portability.HydraCapabilityPackage;
import com.example.editor.portability.HydraCapabilityPackageManifest;
import com.example.editor.portability.MetamodelOrigin;
import com.example.editor.portability.MetamodelOriginKind;
import com.example.editor.portability.orchestration.CapabilityOrchestrationService;
import com.example.editor.release.HydraReleaseGateReport;
import com.example.editor.release.HydraReleaseGateService;
import tools.jackson.databind.json.JsonMapper;

class HydraDistributionProvenanceServiceTests {
    @TempDir Path temporary;

    @Test
    void linksThePackageToAQAndVerifiesThePersistedSbomAttestation() throws Exception {
        CapabilityPackageChecker checker = mock(CapabilityPackageChecker.class);
        HydraReleaseGateService gates = mock(HydraReleaseGateService.class);
        HydraCapabilityPackage pkg = capabilityPackage();
        when(checker.check(pkg)).thenReturn(new CapabilityPackageCheckReport("example.package", List.of()));
        when(gates.find("gate-1")).thenReturn(new HydraReleaseGateReport("gate-1", "hydra.example", "1.0.0",
                Instant.parse("2026-08-04T12:00:00Z"), true, "fingerprint", "", List.of(), "", "",
                List.of(), "a".repeat(64), Map.of()));
        HydraDistributionProvenanceProperties properties = new HydraDistributionProvenanceProperties();
        properties.setStorageRoot(temporary.toString());
        HydraDistributionProvenanceService service = new HydraDistributionProvenanceService(checker,
                mock(CapabilityOrchestrationService.class), mock(UpdateSiteMirrorUpdateService.class),
                mock(UpdateSiteP2MirrorService.class), mock(UpdateSiteAdapterVerificationService.class),
                mock(CapabilityPerformanceAutomationService.class), gates, properties,
                JsonMapper.builder().findAndAddModules().build());

        HydraDistributionProvenanceReport report = service.evaluate(new HydraDistributionProvenanceRequest(
                "hydra.example", "1.0.0", UpdateSiteMirrorChannel.INTERNAL, HydraLicensePolicy.COMPATIBLE_ONLY,
                pkg, "", "", List.of(), "", "gate-1", Map.of("build", "local")));

        assertThat(report.valid()).isTrue();
        assertThat(report.components()).extracting(HydraSbomComponent::type).contains("HYDRA_PACKAGE", "CANONICAL_DOCUMENT", "RELEASE_GATE");
        assertThat(report.components()).filteredOn(component -> component.type().equals("HYDRA_PACKAGE")
                || component.type().equals("CANONICAL_DOCUMENT"))
                .extracting(HydraSbomComponent::origin).containsOnly("file:/models/library.ecore");
        assertThat(service.find(report.reportId())).isEqualTo(report);
        assertThat(service.export(report.reportId())).isNotEmpty();
        assertThat(new String(service.html(report.reportId()), StandardCharsets.UTF_8)).contains("HYDRA distribution inventory", "Verified");
    }

    private HydraCapabilityPackage capabilityPackage() throws Exception {
        var mapper = JsonMapper.builder().findAndAddModules().build();
        MetamodelOrigin origin = new MetamodelOrigin(MetamodelOriginKind.OWNED, null, "file:/models/library.ecore", "", "", "", "", Map.of(), Map.of());
        HydraCapabilityPackageManifest manifest = new HydraCapabilityPackageManifest("1.0", "example.package", "Example", "1.0.0",
                "library", origin, List.of(), List.of(), List.of(), Map.of(), Map.of());
        return new HydraCapabilityPackage(manifest, Map.of("validations.json", mapper.readTree("{\"rules\":[]}")), Map.of());
    }
}
