package com.example.editor.metamodel.importing.updatesite;

import java.util.List;

/** Snapshot creation result plus the analysis obtained from the captured bytes. */
public record UpdateSiteRemoteSnapshotResult(UpdateSiteRemoteSnapshotLock lock,
        UpdateSiteImportAnalysis analysis, boolean reused, List<String> diagnostics) {
    public UpdateSiteRemoteSnapshotResult {
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }
}
