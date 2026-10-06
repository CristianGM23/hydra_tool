package com.example.editor.integration.roundtrip.resolution;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** Auditable result of applying or rolling back one resolution plan. */
public record RoundTripApplyResult(String operationId, boolean successful, boolean rollback,
        List<String> affectedPaths, Path journalPath, Map<String, String> metadata) {
    public RoundTripApplyResult {
        if(operationId == null || operationId.isBlank()){throw new IllegalArgumentException("operationId is required");}
        affectedPaths = affectedPaths == null ? List.of() : List.copyOf(affectedPaths);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
