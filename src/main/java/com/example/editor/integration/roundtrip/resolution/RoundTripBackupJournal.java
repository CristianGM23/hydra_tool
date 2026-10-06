package com.example.editor.integration.roundtrip.resolution;

import java.util.List;

/** Durable rollback journal stored beside file backups. */
public record RoundTripBackupJournal(String operationId, List<RoundTripBackupEntry> entries) {
    public RoundTripBackupJournal {
        if(operationId == null || operationId.isBlank()){throw new IllegalArgumentException("operationId is required");}
        entries = entries == null ? List.of() : List.copyOf(entries);
    }
}
