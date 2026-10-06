package com.example.editor.release;

import java.util.List;
import com.example.editor.system.HydraPlatformReadinessCheck;

/** Combina un release gate persistido con el readiness local antes de publicar. */
public record HydraReleaseCandidateReport(String reportId, boolean releaseGatePassed, boolean platformReady,
        boolean publishable, List<HydraPlatformReadinessCheck> platformChecks) {
    public HydraReleaseCandidateReport {
        reportId = reportId == null ? "" : reportId.trim();
        platformChecks = platformChecks == null ? List.of() : List.copyOf(platformChecks);
    }
}
