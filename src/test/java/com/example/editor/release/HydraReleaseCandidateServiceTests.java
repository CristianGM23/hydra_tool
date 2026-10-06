package com.example.editor.release;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.example.editor.system.HydraPlatformReadinessReport;
import com.example.editor.system.HydraPlatformReadinessService;

class HydraReleaseCandidateServiceTests {

    @Test
    void requiresBothAttestedGateAndPlatformReadiness() {
        HydraReleaseGateService gates = mock(HydraReleaseGateService.class);
        HydraPlatformReadinessService readiness = mock(HydraPlatformReadinessService.class);
        HydraReleaseGateReport passedGate = report("release-gate-pass", true);
        when(gates.find("release-gate-pass")).thenReturn(passedGate);
        when(readiness.evaluate()).thenReturn(new HydraPlatformReadinessReport("HYDRA", "READY", 24, List.of()));
        HydraReleaseCandidateService service = new HydraReleaseCandidateService(gates, readiness);

        HydraReleaseCandidateReport result = service.assess("release-gate-pass");

        assertThat(result.publishable()).isTrue();
        assertThat(service.assertPublishable("release-gate-pass")).isSameAs(passedGate);
    }

    @Test
    void blocksPublicationWhenLocalPlatformIsNotReady() {
        HydraReleaseGateService gates = mock(HydraReleaseGateService.class);
        HydraPlatformReadinessService readiness = mock(HydraPlatformReadinessService.class);
        when(gates.find("release-gate-pass")).thenReturn(report("release-gate-pass", true));
        when(readiness.evaluate()).thenReturn(new HydraPlatformReadinessReport("HYDRA", "NOT_READY", 21, List.of()));
        HydraReleaseCandidateService service = new HydraReleaseCandidateService(gates, readiness);

        assertThat(service.assess("release-gate-pass").publishable()).isFalse();
        assertThatThrownBy(() -> service.assertPublishable("release-gate-pass"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("readiness");
    }

    private HydraReleaseGateReport report(String reportId, boolean passed) {
        return new HydraReleaseGateReport(reportId, "hydra.example", "1.0.0", Instant.now(), passed,
                "fingerprint", "", List.of(), "", "", List.of(), "attestation", Map.of());
    }
}
