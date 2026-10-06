import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';

function source(path: string): string {
    return readFileSync(resolve(process.cwd(), path), 'utf8');
}

describe('HYDRA V4.26.0b comprehensive activity capture contract', () => {
    it('records module and active semantic-context navigation without coupling to individual designers', () => {
        const shell = source('src/application-shell/HydraApplicationShell.tsx');
        expect(shell).toContain("activityType: 'navigation.module.changed'");
        expect(shell).toContain("action: 'NAVIGATE'");
        expect(shell).toContain("'navigation.grammar.changed':'navigation.metamodel.changed'");
        expect(shell).toContain("resourceType: grammar?'grammar':'metamodel'");
        expect(shell).toContain('semanticContextId');
        expect(shell).toContain('previousModule');
        expect(shell).toContain('previousMetamodel');
        expect(shell).toContain('const navigate = (nextModule: HydraModuleId) =>');
        expect(shell).toContain('onNavigate(nextModule)');
        expect(shell).toContain('const changeSemanticContext = (nextContextId: string) =>');
    });

    it('uses a best-effort client audit endpoint that never blocks the user action', () => {
        const api = source('src/audit-log/api.ts');
        expect(api).toContain("'/api/audit-log/activities'");
        expect(api).toContain("method: 'POST'");
        expect(api).toContain('catch');
        expect(api).toContain('JSON.stringify(activity)');
    });

    it('keeps new activity actions visible in Spanish filters', () => {
        const dialog = source('src/audit-log/ActivityLogDialog.tsx');
        expect(dialog).toContain("NAVIGATE: 'Navegación'");
        expect(dialog).toContain("UNDO: 'Deshacer'");
        expect(dialog).toContain("REDO: 'Rehacer'");
        expect(dialog).toContain("CONFIGURE: 'Configuración'");
    });

    it('captures client-only guides, tabs and view navigation without logging every click', () => {
        const tracker = source('src/audit-log/ActivityInteractionTracker.tsx');
        expect(tracker).toContain("role === 'tab'");
        expect(tracker).toContain("className.includes('guide')");
        expect(tracker).toContain("className.includes('preview')");
        expect(tracker).toContain(".hydra-activity-log");
        expect(tracker).toContain('if (!viewCommand) return null');
    });
    it('captures navigation that starts from Home even if the application shell is remounted', () => {
        const home = source('src/application-shell/HydraHome.tsx');
        expect(home).toContain("activityType: 'navigation.module.changed'");
        expect(home).toContain("before: { moduleId: 'HOME' }");
        expect(home).toContain('const navigate = (nextModule: HydraModuleId) =>');
        expect(home).toContain('onNavigate(nextModule)');
        expect(home).toContain('const changeMetamodel = (nextMetamodelId: string) =>');
    });

});
