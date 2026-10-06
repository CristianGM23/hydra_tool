import { describe, expect, it } from 'vitest';
import { DEFAULT_APPEARANCE_SETTINGS } from './appearanceSettings';
import { calculateResponsiveScale, responsiveAppearanceMetrics } from './responsiveSizing';

describe('responsiveSizing', () => {
    it('keeps the configured baseline at the reference viewport', () => {
        expect(calculateResponsiveScale({ width: 1440, height: 900 }, true)).toBe(1);
    });

    it('reduces the interface on a small laptop viewport', () => {
        expect(calculateResponsiveScale({ width: 1280, height: 720 }, true)).toBeLessThan(1);
    });

    it('uses the most restrictive viewport dimension', () => {
        expect(calculateResponsiveScale({ width: 2560, height: 720 }, true)).toBe(0.8);
    });

    it('enlarges the interface on a large screen', () => {
        expect(calculateResponsiveScale({ width: 1920, height: 1200 }, true)).toBeGreaterThan(1);
    });

    it('caps enlargement to protect layout stability', () => {
        expect(calculateResponsiveScale({ width: 8000, height: 5000 }, true)).toBe(1.18);
    });

    it('caps reduction to preserve readability', () => {
        expect(calculateResponsiveScale({ width: 320, height: 480 }, true)).toBe(0.78);
    });

    it('can be disabled without changing configured sizes', () => {
        expect(calculateResponsiveScale({ width: 320, height: 480 }, false)).toBe(1);
    });

    it('falls back safely when viewport measurements are invalid', () => {
        expect(calculateResponsiveScale({ width: Number.NaN, height: 0 }, true)).toBe(1);
    });

    it('scales fonts and controls from the same factor', () => {
        const metrics = responsiveAppearanceMetrics(DEFAULT_APPEARANCE_SETTINGS, { width: 1280, height: 720 });
        expect(metrics.baseFontSize).toBeLessThan(DEFAULT_APPEARANCE_SETTINGS.baseFontSize);
        expect(metrics.controlMinHeight).toBeLessThan(38);
        expect(metrics.checkboxSize).toBeGreaterThanOrEqual(18);
    });

    it('keeps every effective metric within explicit safety limits', () => {
        const metrics = responsiveAppearanceMetrics({ ...DEFAULT_APPEARANCE_SETTINGS, baseFontSize: 32, formFontSize: 32 }, { width: 9000, height: 9000 });
        expect(metrics.baseFontSize).toBeLessThanOrEqual(36);
        expect(metrics.formFontSize).toBeLessThanOrEqual(36);
        expect(metrics.toolbarHeight).toBeLessThanOrEqual(92);
        expect(metrics.controlMinHeight).toBeLessThanOrEqual(48);
        expect(metrics.checkboxSize).toBeLessThanOrEqual(25);
    });

    it('preserves configured values when responsive sizing is disabled', () => {
        const settings = { ...DEFAULT_APPEARANCE_SETTINGS, responsiveSizing: false, formFontSize: 24 };
        const metrics = responsiveAppearanceMetrics(settings, { width: 320, height: 480 });
        expect(metrics.scale).toBe(1);
        expect(metrics.formFontSize).toBe(24);
    });
});
