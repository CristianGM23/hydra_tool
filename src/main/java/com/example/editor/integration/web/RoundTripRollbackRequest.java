package com.example.editor.integration.web;

/** Selects the target directory containing the round-trip backup journal. */
public record RoundTripRollbackRequest(String targetDirectory) {
    public RoundTripRollbackRequest {
        if(targetDirectory == null || targetDirectory.isBlank()){throw new IllegalArgumentException("targetDirectory is required");}
        targetDirectory = targetDirectory.trim();
    }
}
