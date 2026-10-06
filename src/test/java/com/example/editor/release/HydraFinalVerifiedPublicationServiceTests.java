package com.example.editor.release;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.example.editor.provenance.HydraDistributionProvenanceService;
import com.example.editor.standalone.publication.DistributionPublicationService;
import com.example.editor.standalone.publication.PublicationArtifactRequest;
import com.example.editor.standalone.publication.PublicationChannel;
import com.example.editor.standalone.publication.PublicationRequest;
import com.example.editor.standalone.publication.PublicationResult;
import com.example.editor.standalone.publication.PublicationSigningMode;

class HydraFinalVerifiedPublicationServiceTests {

    @Test
    void publishesOnlyAfterReleaseReadinessAndProvenancePass() {
        HydraReleaseCandidateService candidates = mock(HydraReleaseCandidateService.class);
        HydraDistributionProvenanceService provenance = mock(HydraDistributionProvenanceService.class);
        DistributionPublicationService publications = mock(DistributionPublicationService.class);
        PublicationRequest publication = publication("hydra.example", "1.0.0");
        PublicationResult expected = mock(PublicationResult.class);
        when(candidates.assertPublishable("gate-1")).thenReturn(gate("gate-1", "hydra.example", "1.0.0"));
        when(publications.publish(publication)).thenReturn(expected);
        HydraFinalVerifiedPublicationService service = new HydraFinalVerifiedPublicationService(candidates, provenance, publications);

        PublicationResult result = service.publish(new HydraFinalVerifiedPublicationRequest("gate-1", "sbom-1", publication));

        assertThat(result).isSameAs(expected);
        verify(provenance).assertValid("sbom-1", "hydra.example", "1.0.0");
        verify(publications).publish(publication);
    }

    @Test
    void rejectsIdentityThatDiffersFromReleaseGateBeforePublishing() {
        HydraReleaseCandidateService candidates = mock(HydraReleaseCandidateService.class);
        HydraDistributionProvenanceService provenance = mock(HydraDistributionProvenanceService.class);
        DistributionPublicationService publications = mock(DistributionPublicationService.class);
        PublicationRequest publication = publication("hydra.other", "1.0.0");
        when(candidates.assertPublishable("gate-1")).thenReturn(gate("gate-1", "hydra.example", "1.0.0"));
        HydraFinalVerifiedPublicationService service = new HydraFinalVerifiedPublicationService(candidates, provenance, publications);

        assertThatThrownBy(() -> service.publish(new HydraFinalVerifiedPublicationRequest("gate-1", "sbom-1", publication)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("productId");
    }

    private PublicationRequest publication(String productId, String version) {
        return new PublicationRequest(productId, version, PublicationChannel.INTERNAL, PublicationSigningMode.CHECKSUM_ONLY,
                "distribution", "", List.of(new PublicationArtifactRequest("web", "hydra.zip", "application/zip")), Map.of());
    }

    private HydraReleaseGateReport gate(String reportId, String productId, String version) {
        return new HydraReleaseGateReport(reportId, productId, version, Instant.now(), true, "fingerprint", "",
                List.of(), "", "", List.of(), "attestation", Map.of());
    }
}
