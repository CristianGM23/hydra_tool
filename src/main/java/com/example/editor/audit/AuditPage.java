package com.example.editor.audit;

import java.util.List;

public record AuditPage(List<AuditEntry> entries, int page, int pageSize, boolean hasMore) {
    public AuditPage {
        entries = entries == null ? List.of() : List.copyOf(entries);
    }
}
