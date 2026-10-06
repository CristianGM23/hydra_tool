import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';

function source(path: string): string {
    const candidates = [resolve(process.cwd(), path), resolve(process.cwd(), 'frontend', path)];
    const found = candidates.find(candidate => {
        try { readFileSync(candidate); return true; } catch { return false; }
    });
    if (found === undefined) throw new Error(`Could not locate ${path}`);
    return readFileSync(found, 'utf8');
}

describe('HYDRA lazy module loading contract', () => {
    it('loads independent application modules and designers through dynamic imports', () => {
        const app = source('src/App.tsx');
        const capabilityModule = source('src/application-shell/HydraCapabilityModule.tsx');
        const workbench = source('src/application-shell/IndependentCapabilityWorkbench.tsx');
        const metamodelDesigner = source('src/metamodel-designer/MetamodelDesigner.tsx');
        expect(capabilityModule).toContain("lazy(() => import('../update-site-import-center/UpdateSiteImportCenter')");
        expect(capabilityModule).toContain("lazy(() => import('../metamodel-designer/MetamodelDesigner')");
        expect(app).toContain('HydraCapabilityModule');
        expect(app).toContain('<Suspense fallback={<HydraModuleLoading');
        expect(workbench).toContain("lazy(() => import('../event-designer/EventDesigner')");
        expect(workbench).toContain("lazy(() => import('../gamification-designer/GamificationDesigner')");
        expect(metamodelDesigner).toContain("lazy(() => import('../validation-designer/ValidationDesigner')");
        expect(metamodelDesigner).toContain("lazy(() => import('../integration-designer/IntegrationDesigner')");
        expect(metamodelDesigner).toContain("import './metamodel-designer.css'");
    });

    it('enforces configurable raw, gzip and total JavaScript budgets after production builds', () => {
        const packageJson = JSON.parse(source('package.json')) as { scripts: Record<string, string> };
        const budget = JSON.parse(source('config/hydra-bundle-budget.json')) as Record<string, number>;
        expect(packageJson.scripts.build).toContain('bundle:check');
        expect(packageJson.scripts['bundle:check']).toContain('check-bundle-budget.mjs');
        expect(budget.entryMaxBytes).toBeGreaterThan(0);
        expect(budget.asyncChunkMaxGzipBytes).toBeGreaterThan(0);
        expect(budget.totalJavaScriptMaxBytes).toBeGreaterThan(budget.entryMaxBytes);
    });
});
