import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';

const css = readFileSync(resolve(process.cwd(), 'src/designer-ui/designer-ui.css'), 'utf8');

describe('designer control layout contract', () => {
    it('keeps contextual information controls circular despite generic pill styles', () => {
        expect(css).toContain('.hydra-field-info-button {');
        expect(css).toContain('min-height: 21px !important');
        expect(css).toContain('border-radius: 50% !important');
        expect(css).toContain('.hydra-resizable-info-button {');
        expect(css).toContain('aspect-ratio: 1');
    });

    it('keeps palette action labels visible at large font sizes', () => {
        expect(css).toContain('.hydra-sidebar-section-actions button');
        expect(css).toContain('white-space: normal');
        expect(css).toContain('overflow-wrap: anywhere');
        expect(css).toContain('font-size: clamp(11px');
    });

    it('provides a keyboard-focusable shared palette separator', () => {
        expect(css).toContain('.hydra-designer-sidebar-resizer');
        expect(css).toContain('cursor: col-resize');
        expect(css).toContain('--hydra-designer-sidebar-width');
    });

    it('keeps guide icons square before applying the circular radius', () => {
        expect(css).toContain('flex: 0 0 18px');
        expect(css).toContain('max-height: 18px');
    });

    it('allows button labels to use several lines', () => {
        expect(css).toContain('line-height: 1.15');
        expect(css).toContain('text-align: center');
    });

    it('caps palette action text independently of oversized global fonts', () => {
        expect(css).toContain('calc(var(--app-form-font-size, 18px) * .76)');
        expect(css).toContain('14px) !important');
    });

    it('persists a real three-column desktop layout', () => {
        expect(css).toContain('310px) 7px minmax(0, 1fr)');
    });

    it('removes the resize separator on narrow screens', () => {
        expect(css).toContain('@media (max-width: 760px)');
        expect(css.replace(/\s+/g, '')).toContain('.hydra-designer-sidebar-resizer{display:none;}');
    });

    it('uses container queries to reorganize narrow palette headings', () => {
        expect(css).toContain('@container hydra-designer-sidebar (max-width: 330px)');
        expect(css).toContain('flex-direction: column');
    });

    it('prevents tooltip controls from inheriting generic button padding', () => {
        expect(css).toContain('padding: 0 !important');
        expect(css).toContain('max-width: 21px !important');
    });
});
