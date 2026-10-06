import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';

function source(path: string): string { return readFileSync(resolve(process.cwd(), path), 'utf8'); }

describe('HYDRA V4.32.3 global dark-theme convergence', () => {
    it('loads the global dark convergence layer after accessibility', () => {
        const main = source('src/main.tsx');
        expect(main).toContain("./design-system/hydra-dark-theme-convergence.css");
        expect(main.indexOf('hydra-dark-theme-convergence.css')).toBeGreaterThan(main.indexOf('hydra-accessibility.css'));
    });

    it('covers Home, every principal workbench and React Flow chrome', () => {
        const css = source('src/design-system/hydra-dark-theme-convergence.css');
        for (const selector of [
            '.hydra-home', '.model-editor-workbench', '.metamodel-visual-designer',
            '.editor-definition-designer', '.validation-designer', '.event-designer',
            '.event-flow-designer', '.gamification-designer', '.integration-designer',
            '.project-workbench-shell', '.update-site-import-center', '.artifact-registry-center',
            '.repository-connections-center', '.standalone-distribution-center',
            '.generated-project-center', '.publication-center', '.representable-import-center',
            '.roundtrip-center', '.native-compatibility-center', '.react-flow__controls',
            '.react-flow__minimap', '.react-flow__edge-textbg'
        ]) expect(css).toContain(selector);
    });

    it('keeps Home chrome semantic instead of pinning light-theme colors', () => {
        const css = source('src/application-shell/application-shell.css');
        expect(css).toContain('background:var(--hydra-bg-app)');
        expect(css).toContain('background:var(--hydra-bg-hover)');
        expect(css).toContain('color:var(--hydra-text-primary)');
        expect(css).not.toContain('.hydra-home{display:grid;grid-template-columns:210px minmax(0,1fr);gap:0;width:100%;height:100%;min-height:0;padding:0;overflow:hidden;background:#f5f7fb');
    });

    it('retains persisted notation colors as model data, not UI theme data', () => {
        expect(source('src/visual-style/visualStyleUtils.ts')).toContain("fillColor: '#ffffff'");
        expect(source('src/notation/notationUtils.ts')).toContain("fillColor: '#ffffff'");
        expect(source('src/design-system/hydra-dark-theme-convergence.css')).not.toContain('.react-flow__node {');
    });
});
