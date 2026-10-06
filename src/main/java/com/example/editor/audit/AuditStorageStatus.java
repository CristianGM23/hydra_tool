package com.example.editor.audit;

/** Estado visible del almacenamiento local y de su política de retención. */
public record AuditStorageStatus(String provider, boolean maintenanceSupported, long usedBytes, long maximumBytes, int partitionCount, boolean retentionEnabled,
        int navigationRetentionDays, int openRetentionDays, int routineRetentionDays, int retentionDays) { }
