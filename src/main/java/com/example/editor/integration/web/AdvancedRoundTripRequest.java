package com.example.editor.integration.web;

import java.util.List;
import com.example.editor.integration.roundtrip.resolution.ElementConflictDecision;

/** Explicit decisions for an advanced round-trip preview or application. */
public record AdvancedRoundTripRequest(String targetDirectory, List<ElementConflictDecision> decisions) {
    public AdvancedRoundTripRequest {
        if(targetDirectory == null || targetDirectory.isBlank()){throw new IllegalArgumentException("targetDirectory is required");}
        targetDirectory = targetDirectory.trim();
        decisions = decisions == null ? List.of() : List.copyOf(decisions);
    }
}
