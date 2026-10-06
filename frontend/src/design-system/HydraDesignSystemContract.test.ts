import { readCssBundle } from '../test-support/readCssBundle';
import { describe, expect, it } from 'vitest';
import * as designSystem from './index';

const exportedComponents = [
    'HydraNavBar', 'HydraButton', 'HydraTooltip', 'HydraResizablePanel', 'HydraFormField',
    'HydraSelect', 'HydraTree', 'HydraModal', 'HydraPalette'
] as const;

function readDesignSystemCss(): string { return readCssBundle('src/design-system/hydra-design-system.css'); }

describe('HYDRA design-system public contract', () => {
    it('exports every requested primitive from one public entry point', () => {
        for (const component of exportedComponents) {
            expect(designSystem).toHaveProperty(component);
            const primitive = designSystem[component] as unknown;
            const renderable = typeof primitive === 'function'
                || (typeof primitive === 'object' && primitive !== null && '$$typeof' in primitive);
            expect(renderable).toBe(true);
        }
    });

    it('defines shared tokens for controls, focus, panels, modals and responsive behavior', () => {
        const designSystemCss = readDesignSystemCss();
        expect(designSystemCss).toContain('--hydra-control-height');
        expect(designSystemCss).toContain('--hydra-focus');
        expect(designSystemCss).toContain('.hydra-ui-modal');
        expect(designSystemCss).toContain('.hydra-ui-panel-resizer');
        expect(designSystemCss).toContain('@container');
    });
});
