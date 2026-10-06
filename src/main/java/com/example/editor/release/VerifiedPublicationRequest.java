package com.example.editor.release;

import com.example.editor.standalone.publication.PublicationRequest;

/** Existing publication request guarded by one passed AQ report. */
public record VerifiedPublicationRequest(String gateReportId, PublicationRequest publication) {
    public VerifiedPublicationRequest {gateReportId = gateReportId == null ? "" : gateReportId.trim();}
}
