package com.example.editor.provenance;

import com.example.editor.standalone.publication.PublicationRequest;

/** Existing publication request guarded by an AV provenance report. */
public record ProvenanceVerifiedPublicationRequest(String provenanceReportId,
        PublicationRequest publication) {
    public ProvenanceVerifiedPublicationRequest {
        provenanceReportId = provenanceReportId == null ? "" : provenanceReportId.trim();
    }
}
