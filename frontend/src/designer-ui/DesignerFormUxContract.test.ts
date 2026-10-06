import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';

const designerCss = readFileSync(resolve(process.cwd(), 'src/designer-ui/designer-ui.css'), 'utf8');
const structureCss = readFileSync(resolve(process.cwd(), 'src/designer-ui/designer-structure.css'), 'utf8');
const exportCss = readFileSync(resolve(process.cwd(), 'src/metamodel-designer/metamodel-export-dialog.css'), 'utf8');

describe('designer form and export UX contracts', () => {
    it('keeps number inputs compact', () => {
        expect(designerCss).toContain("input[type='number']");
        expect(designerCss).toContain('width: min(10rem, 100%)');
    });

    it('provides an explicit compact field class', () => {
        expect(designerCss).toContain('.hydra-designer-compact');
        expect(designerCss).toContain('justify-self: start');
    });

    it('uses three columns for semantic, definition and properties panes', () => {
        expect(structureCss).toContain('.designer-structure-triple-layout');
        expect(structureCss).toContain('grid-template-columns: minmax(250px, .8fr) minmax(280px, 1fr) minmax(360px, 1.25fr)');
    });

    it('keeps the three-pane layout responsive', () => {
        expect(structureCss).toMatch(/@media\(max-width:960px\)[\s\S]*designer-structure-triple-layout[\s\S]*grid-template-columns: 1fr/);
    });

    it('highlights the active palette connection tool', () => {
        expect(structureCss).toContain('.designer-connection-tool.active');
    });

    it('aligns each export format and download action horizontally', () => {
        expect(exportCss).toMatch(/\.metamodel-export-option-selectable\s*\{[\s\S]*grid-template-columns: minmax\(0, 1fr\) auto/);
        expect(exportCss).toContain('min-width: 104px');
    });
});
