import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';

function source(path: string): string {
    return readFileSync(resolve(process.cwd(), path), 'utf8');
}

describe('HYDRA V4.26.0c activity retention and deletion contract', () => {
    it('uses tiered retention and a hard storage ceiling', () => {
        const properties = source('../src/main/java/com/example/editor/audit/AuditLogProperties.java');
        const config = source('../src/main/resources/application.properties');
        expect(properties).toContain('navigationRetentionDays = 7');
        expect(properties).toContain('openRetentionDays = 14');
        expect(properties).toContain('routineRetentionDays = 60');
        expect(properties).toContain('maximumStorageBytes = 134217728L');
        expect(config).toContain('hydra.audit.maximum-storage-bytes=134217728');
    });

    it('compacts mixed JSONL partitions instead of deleting only complete old days', () => {
        const store = source('../src/main/java/com/example/editor/audit/JsonlAuditLogStore.java');
        expect(store).toContain('implements AuditLogStore, AuditLogMaintenance');
        expect(store).toContain('rewritePartition(path, entry -> retentionPolicy.isExpired(entry, now), cleanup)');
        expect(store).toContain('StandardCopyOption.ATOMIC_MOVE');
        expect(store).toContain('evictionPriority(entry) == currentPriority');
    });

    it('flushes pending events before maintenance and scopes deletion to current actor and workspace', () => {
        const controller = source('../src/main/java/com/example/editor/audit/AuditLogController.java');
        expect(controller).toContain('@PostMapping("/maintenance/delete")');
        expect(controller).toContain('publisher.flushPending();');
        expect(controller).toContain('new AuditDeletionCriteria(workspaceId, Set.of(actorId)');
    });

    it('exposes explicit safe maintenance controls in the Activity Log UI', () => {
        const dialog = source('src/audit-log/ActivityLogDialog.tsx');
        const api = source('src/audit-log/api.ts');
        expect(dialog).toContain("setPendingDelete('NAVIGATION')");
        expect(dialog).toContain("setPendingDelete('FILTERED')");
        expect(dialog).toContain("setPendingDelete('ALL')");
        expect(dialog).toContain('hasDeletionFilter');
        expect(api).toContain("'/api/audit-log/maintenance/delete'");
        expect(api).toContain("'/api/audit-log/maintenance/run'");
    });
});
