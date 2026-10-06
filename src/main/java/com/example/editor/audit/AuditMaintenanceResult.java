package com.example.editor.audit;

/** Resultado estable para limpieza automática y borrados manuales. */
public record AuditMaintenanceResult(long deletedEntries, long reclaimedBytes, long remainingBytes, int partitionsTouched, String reason) {
    public AuditMaintenanceResult {
        reason = reason == null ? "" : reason;
    }
}
