package com.example.editor.audit;

import java.util.List;

public record AuditLogViewPage(List<AuditPresentedEntry> entries, int page, int pageSize, boolean hasMore) { }
