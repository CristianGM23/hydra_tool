package com.example.editor.integration.roundtrip.resolution;

/** One file state stored in an apply journal for deterministic rollback. */
public record RoundTripBackupEntry(String path, boolean existedBefore) {
    public RoundTripBackupEntry {
        if(path == null || path.isBlank()){throw new IllegalArgumentException("path is required");}
        path = path.replace('\\', '/').trim();
    }
}
