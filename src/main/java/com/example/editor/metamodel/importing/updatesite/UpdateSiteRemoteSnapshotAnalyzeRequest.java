package com.example.editor.metamodel.importing.updatesite;

/** Offline reanalysis request for an existing remote snapshot. */
public record UpdateSiteRemoteSnapshotAnalyzeRequest(String snapshotId, UpdateSiteImportOptions options) {
    public UpdateSiteRemoteSnapshotAnalyzeRequest {
        snapshotId = snapshotId == null ? "" : snapshotId.trim();
        options = options == null ? UpdateSiteImportOptions.defaults() : options;
    }
}
