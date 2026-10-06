package com.example.editor.integration.roundtrip;

import java.util.Set;

/** Parámetros REST para sincronización selectiva. */
public record RoundTripSyncRequest(String targetDirectory, Set<String> selectedPaths, boolean preserveManualRegions) {
    public RoundTripSyncRequest {
        if(targetDirectory == null || targetDirectory.isBlank()){throw new IllegalArgumentException("targetDirectory is required");}
        targetDirectory = targetDirectory.trim(); selectedPaths = selectedPaths == null ? Set.of() : Set.copyOf(selectedPaths);
    }
}
