package com.example.editor.metamodel.importing.updatesite;

import java.util.List;

/** Verified offline input set exposed only after lockfile and object validation. */
public record UpdateSiteRemoteSnapshotOperations(String snapshotId, List<UpdateSiteInputFile> inputs) {
    public UpdateSiteRemoteSnapshotOperations {
        snapshotId = snapshotId == null ? "" : snapshotId;
        inputs = inputs == null ? List.of() : List.copyOf(inputs);
    }
}
