package com.example.editor.metamodel.importing.updatesite;

import java.time.Instant;
import java.util.List;

/** Deterministic mirror comparison, promotion and rollback evidence. */
public record UpdateSiteMirrorUpdateReport(String reportId, String baselineMirrorId,
        String candidateMirrorId, UpdateSiteMirrorChannel channel, Instant createdAt,
        int added, int removed, int changed, int unchanged,
        List<UpdateSiteMirrorDeltaItem> deltas, boolean rollbackValid,
        boolean promotable, boolean promoted, List<String> diagnostics) {
    public UpdateSiteMirrorUpdateReport {
        createdAt = createdAt == null ? Instant.now() : createdAt;
        deltas = deltas == null ? List.of() : List.copyOf(deltas);
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }
}
