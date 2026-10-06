package com.example.editor.metamodel.importing.updatesite;

import java.util.List;

/** Compares a stored lockfile with the current allowlisted remote resources. */
public record UpdateSiteRemoteSnapshotDriftReport(String snapshotId, boolean drifted,
        int unchanged, int changed, int unavailable, List<UpdateSiteRemoteSnapshotDriftItem> items) {
    public UpdateSiteRemoteSnapshotDriftReport {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
