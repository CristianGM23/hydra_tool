package com.example.editor.release;

import com.example.editor.standalone.publication.PublicationRequest;

/** Solicitud final que exige release gate, SBOM/procedencia y readiness local. */
public record HydraFinalVerifiedPublicationRequest(String releaseGateReportId, String provenanceReportId, PublicationRequest publication) {
    public HydraFinalVerifiedPublicationRequest {
        releaseGateReportId = releaseGateReportId == null ? "" : releaseGateReportId.trim();
        provenanceReportId = provenanceReportId == null ? "" : provenanceReportId.trim();
    }
}
