import { readCssBundle } from '../test-support/readCssBundle';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';

function source(path: string): string {
    return readFileSync(resolve(process.cwd(), path), 'utf8');
}

const css = readCssBundle('src/design-system/hydra-design-system.css');

describe('global tooltip information control style', () => {
    it('uses one globally loaded selector for every information button', () => {
        expect(css).toContain('.hydra-context-help-button, .hydra-ui-tooltip-trigger, .hydra-field-info-button, .hydra-resizable-info-button, .validation-field-info-button, .app-field-info-button, .validation-branch-help-text');
    });

    it('routes every default and custom tooltip trigger through the shared class', () => {
        for (const path of [
            'src/design-system/HydraTooltip.tsx',
            'src/designer-ui/DesignerFieldHelp.tsx',
            'src/designer-ui/ResizableInfoTooltip.tsx',
            'src/validation-designer/ValidationFieldHelp.tsx'
        ]) expect(source(path), path).toContain('hydra-context-help-button');
        expect(source('src/validation-designer/ExpressionBuilder.tsx')).toContain('<HelpedField');
        expect(source('src/validation-designer/ValidationFieldHelp.tsx')).toContain('tooltipTriggerClassName="validation-field-info-button"');
    });

    it('matches the 23 pixel Rules information control', () => {
        expect(css).toContain('inline-size: 23px !important');
        expect(css).toContain('block-size: 23px !important');
        expect(css).toContain('font-size: 15px !important');
    });

    it('forces the same circular border, background and focus treatment', () => {
        expect(css).toContain('aspect-ratio: 1 / 1 !important');
        expect(css).toContain('border-radius: 50% !important');
        expect(css).toContain('background: var(--hydra-color-primary-subtle) !important');
        expect(css).toContain('color: var(--hydra-color-primary-700) !important');
        expect(css).toContain('border-color: var(--hydra-color-primary-600) !important');
        expect(css).toContain('background: var(--hydra-bg-selection) !important');
        expect(css).toContain('outline: 3px solid rgb(49 95 206 / 28%) !important');
    });
});
