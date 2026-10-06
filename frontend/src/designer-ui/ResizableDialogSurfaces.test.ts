import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';

const globalCss = readFileSync(resolve(process.cwd(), 'src/index.css'), 'utf8');
const designerCss = readFileSync(resolve(process.cwd(), 'src/designer-ui/designer-ui.css'), 'utf8');
const tooltipSource = readFileSync(resolve(process.cwd(), 'src/design-system/HydraTooltip.tsx'), 'utf8');

describe('resizable HYDRA help and dialog surfaces', () => {
    it('keeps every non-maximized button dialog resizable', () => {
        expect(globalCss).toContain('body [role="dialog"]:not(.maximized)');
        expect(globalCss).toContain('resize: both;');
        expect(globalCss).toContain('max-width: calc(100vw - 24px);');
        expect(globalCss).toContain('max-height: calc(100vh - 24px);');
    });

    it('keeps field help resizable and rendered with viewport coordinates', () => {
        expect(designerCss).toContain('.hydra-field-tooltip');
        expect(designerCss).toContain('resize: both;');
        expect(designerCss).toContain('position: fixed;');
        expect(tooltipSource).toContain('style={{ left: position.left, top: position.top }}');
    });
});
