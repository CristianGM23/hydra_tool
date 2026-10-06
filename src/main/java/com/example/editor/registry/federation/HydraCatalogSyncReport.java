package com.example.editor.registry.federation;

import java.time.Instant;
import java.util.List;

/** Retained BB synchronization evidence. */
public record HydraCatalogSyncReport(String reportId, String sourceId, HydraCatalogTrustLevel trustLevel,
        String indexSha256, List<String> addedEntryIds, List<String> updatedEntryIds,
        List<String> unchangedEntryIds, List<String> rejectedEntryIds,
        List<HydraCatalogSyncConflict> conflicts, boolean applied, List<String> diagnostics,
        Instant synchronizedAt) {
    public HydraCatalogSyncReport {
        addedEntryIds=addedEntryIds==null?List.of():List.copyOf(addedEntryIds);
        updatedEntryIds=updatedEntryIds==null?List.of():List.copyOf(updatedEntryIds);
        unchangedEntryIds=unchangedEntryIds==null?List.of():List.copyOf(unchangedEntryIds);
        rejectedEntryIds=rejectedEntryIds==null?List.of():List.copyOf(rejectedEntryIds);
        conflicts=conflicts==null?List.of():List.copyOf(conflicts);
        diagnostics=diagnostics==null?List.of():List.copyOf(diagnostics);
        synchronizedAt=synchronizedAt==null?Instant.now():synchronizedAt;
    }
}
