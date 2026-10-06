package com.example.editor.metamodel.importing.updatesite;

import java.util.List;

/** Explicit provenance and license review for every resource included in an exportable snapshot. */
public record UpdateSiteRemoteSnapshotLicenseReviewRequest(List<UpdateSiteRemoteSnapshotLicenseEntry> licenses) {
    public UpdateSiteRemoteSnapshotLicenseReviewRequest {
        licenses = licenses == null ? List.of() : List.copyOf(licenses);
    }
}
