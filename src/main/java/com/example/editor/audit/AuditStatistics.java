package com.example.editor.audit;

import java.util.Map;

public record AuditStatistics(long total, long created, long updated, long deleted, long exports,
        Map<String, Long> byModule, Map<String, Long> byAction) {
    public AuditStatistics {
        byModule = byModule == null ? Map.of() : Map.copyOf(byModule);
        byAction = byAction == null ? Map.of() : Map.copyOf(byAction);
    }
}
