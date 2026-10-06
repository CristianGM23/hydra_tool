import { describe, expect, it } from 'vitest';
import { DEFAULT_APPEARANCE_SETTINGS, FONT_SIZE_OPTIONS, fontFamilyCss, normalizeAppearanceSettings } from './appearanceSettings';

describe('appearanceSettings', () => {
    it('provides at least four larger font levels', () => {
        expect(FONT_SIZE_OPTIONS.filter(size => size > DEFAULT_APPEARANCE_SETTINGS.formFontSize).length).toBeGreaterThanOrEqual(4);
    });

    it('defaults forms to a visibly larger size than the previous 12-13px controls', () => {
        expect(DEFAULT_APPEARANCE_SETTINGS.formFontSize).toBeGreaterThanOrEqual(17);
        expect(DEFAULT_APPEARANCE_SETTINGS.windowFontSize).toBeGreaterThanOrEqual(18);
        expect(DEFAULT_APPEARANCE_SETTINGS.diagramFontSize).toBeGreaterThanOrEqual(18);
        expect(DEFAULT_APPEARANCE_SETTINGS.tooltipFontSize).toBeGreaterThanOrEqual(18);
        expect(DEFAULT_APPEARANCE_SETTINGS.responsiveSizing).toBe(true);
    });

    it('normalizes invalid persisted settings', () => {
        expect(normalizeAppearanceSettings({ fontFamily: 'UNKNOWN' as never, formFontSize: 999 })).toEqual(DEFAULT_APPEARANCE_SETTINGS);
    });

    it('preserves an explicitly disabled responsive mode', () => {
        expect(normalizeAppearanceSettings({ responsiveSizing: false }).responsiveSizing).toBe(false);
    });

    it('maps configured families to complete CSS stacks', () => {
        expect(fontFamilyCss('VERDANA')).toContain('Verdana');
        expect(fontFamilyCss('MONOSPACE')).toContain('Consolas');
    });
});
