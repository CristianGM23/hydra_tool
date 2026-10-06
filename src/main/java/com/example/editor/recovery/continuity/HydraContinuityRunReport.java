package com.example.editor.recovery.continuity;

import java.time.Instant;
import java.util.List;

/** Comparable BE RPO/RTO evidence. */
public record HydraContinuityRunReport(String runId, String planId, String backupId,
        boolean passed, long measuredRpoMinutes, long measuredRtoMillis,
        boolean rpoSatisfied, boolean rtoSatisfied, List<String> rotatedBackupIds,
        List<String> diagnostics, Instant completedAt) {
    public HydraContinuityRunReport {
        rotatedBackupIds=rotatedBackupIds==null?List.of():List.copyOf(rotatedBackupIds);
        diagnostics=diagnostics==null?List.of():List.copyOf(diagnostics);
        completedAt=completedAt==null?Instant.now():completedAt;
    }
}
