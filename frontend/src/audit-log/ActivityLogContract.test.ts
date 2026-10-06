import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';

function source(path: string): string {
    return readFileSync(resolve(process.cwd(), path), 'utf8');
}

describe('HYDRA V4.26 user activity log contract', () => {
    it('is globally reachable and keeps log presentation language independent', () => {
        const shell = source('src/application-shell/HydraApplicationShell.tsx');
        const dialog = source('src/audit-log/ActivityLogDialog.tsx');
        expect(shell).toContain('<ActivityLogDialog');
        expect(shell).toContain("t('Activity log')");
        expect(dialog).toContain('data-hydra-i18n-skip="true"');
        expect(dialog).toContain("useState<AuditLanguage>('en')");
        expect(dialog).toContain("en:");
        expect(dialog).toContain("es:");
    });

    it('offers every requested export without changing canonical data', () => {
        const dialog = source('src/audit-log/ActivityLogDialog.tsx');
        const api = source('src/audit-log/api.ts');
        expect(dialog).toContain("['JSON', 'CSV', 'MARKDOWN', 'TXT', 'DOC']");
        expect(api).toContain('/api/audit-log/export');
        expect(api).toContain('language');
        expect(api).toContain('format');
    });

    it('keeps the viewer paginated and filter application explicit', () => {
        const dialog = source('src/audit-log/ActivityLogDialog.tsx');
        expect(dialog).toContain('hasMore');
        expect(dialog).toContain('draftFilters');
        expect(dialog).toContain('applyFilters');
        expect(dialog).toContain('page + 1');
    });


    it('renders activity dropdowns above modal content through the global overlay layer', () => {
        const dropdown = source('src/designer-ui/DesignerDropdown.tsx');
        const styles = source('src/designer-ui/designer-dropdown.css');
        const theme = source('src/design-system/hydra-theme.css');
        expect(dropdown).toContain('HydraOverlayPortal');
        expect(dropdown).toContain('layer="DROPDOWN"');
        expect(styles).toContain('var(--hydra-z-dropdown');
        expect(theme).toContain('--hydra-z-modal: 20000');
        expect(theme).toContain('--hydra-z-dropdown: 24000');
    });

    it('uses the common HYDRA modal and responsive visual timeline', () => {
        const dialog = source('src/audit-log/ActivityLogDialog.tsx');
        const styles = source('src/audit-log/activity-log.css');
        expect(dialog).toContain('<HydraModal');
        expect(styles).toContain('.hydra-activity-timeline');
        const compact = styles.replace(/\s+/g, '');
        expect(compact).toContain('max-height:min(54vh,650px)');
        expect(compact).toContain('@media(max-width:820px)');
    });
});
