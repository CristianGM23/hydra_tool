package com.example.editor.recovery;

import java.time.Instant;
import java.util.List;

/** AZ verification and isolated restore report. */
public record HydraRecoveryDrillReport(String drillId,String backupId,boolean passed,String restoreRoot,
        int verifiedFiles,List<String> diagnostics,Instant completedAt) {
    public HydraRecoveryDrillReport {diagnostics=diagnostics==null?List.of():List.copyOf(diagnostics);completedAt=completedAt==null?Instant.now():completedAt;}
}
