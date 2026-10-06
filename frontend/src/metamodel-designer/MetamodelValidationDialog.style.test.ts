import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';

const css = readFileSync(resolve(process.cwd(), 'src/metamodel-designer/metamodel-validation-dialog.css'), 'utf8');
const component = readFileSync(resolve(process.cwd(), 'src/metamodel-designer/MetamodelValidationDialog.tsx'), 'utf8');

describe('MetamodelValidationDialog visual contract', () => {
    it('uses the shared opaque modal instead of a transparent legacy surface', () => {
        expect(component).toContain('HydraModal');
        expect(component).not.toContain('createPortal');
        expect(css).toContain('var(--hydra-color-surface, var(--hydra-bg-surface))');
        expect(css).toContain('var(--hydra-color-surface-muted, var(--hydra-bg-surface-muted))');
        expect(css).toContain('var(--hydra-border-default)');
        expect(css).not.toContain('var(--hydra-radius-panel)');
    });

    it('keeps findings scrollable and responsive inside the modal', () => {
        const compact = css.replace(/\s+/g, '');
        expect(compact).toContain('grid-template-rows:autoautoautominmax(0,1fr)');
        expect(compact).toContain('overflow:auto');
        expect(compact).toContain('@media(max-width:760px)');
    });
});
