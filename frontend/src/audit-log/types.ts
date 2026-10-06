export type AuditLanguage = 'en' | 'es';
export type AuditExportFormat = 'JSON' | 'CSV' | 'MARKDOWN' | 'TXT' | 'DOC';

export interface AuditPresentedChange {
    label: string;
    path: string;
    kind: 'ADDED' | 'CHANGED' | 'REMOVED';
    beforeValue: string;
    afterValue: string;
}

export interface AuditPresentedEntry {
    eventId: string;
    timestamp: string;
    title: string;
    summary: string;
    moduleId: string;
    module: string;
    action: string;
    actionLabel: string;
    importance: 'INFO' | 'IMPORTANT' | 'WARNING' | 'SECURITY';
    result: 'SUCCESS' | 'PARTIAL' | 'FAILED';
    resourceType: string;
    resourceId: string;
    resourceName: string;
    actorId: string;
    actorName: string;
    changes: AuditPresentedChange[];
    changesTruncated: boolean;
    correlationId: string;
    metadata: Record<string, unknown>;
}

export interface AuditLogViewPage {
    entries: AuditPresentedEntry[];
    page: number;
    pageSize: number;
    hasMore: boolean;
}

export interface AuditStatistics {
    total: number;
    created: number;
    updated: number;
    deleted: number;
    exports: number;
    byModule: Record<string, number>;
    byAction: Record<string, number>;
}

export interface AuditDescriptorView {
    activityType: string;
    moduleId: string;
    action: string;
    title: string;
    module: string;
    importance: string;
}

export interface AuditQueryFilters {
    text?: string;
    module?: string;
    action?: string;
    from?: string;
    to?: string;
}

export interface AuditClientActivityRequest {
    activityType: string;
    moduleId: string;
    action: string;
    importance?: 'INFO' | 'IMPORTANT' | 'WARNING' | 'SECURITY';
    resourceType: string;
    resourceId: string;
    displayName: string;
    before?: unknown;
    after?: unknown;
    metadata?: Record<string, unknown>;
    dedupeKey?: string;
}

export type AuditDeletionMode = 'NAVIGATION' | 'FILTERED' | 'ALL';

export interface AuditStorageStatus {
    provider: string;
    maintenanceSupported: boolean;
    usedBytes: number;
    maximumBytes: number;
    partitionCount: number;
    retentionEnabled: boolean;
    navigationRetentionDays: number;
    openRetentionDays: number;
    routineRetentionDays: number;
    retentionDays: number;
}

export interface AuditMaintenanceResult {
    deletedEntries: number;
    reclaimedBytes: number;
    remainingBytes: number;
    partitionsTouched: number;
    reason: string;
}
