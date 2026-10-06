import { describe, expect, it } from 'vitest';
import { clampDesignerSidebarWidth } from './useResizableDesignerSidebar';

describe('resizable designer sidebars', () => {
    it('keeps palette widths inside accessible bounds', () => {
        expect(clampDesignerSidebarWidth(120, 230, 620)).toBe(230);
        expect(clampDesignerSidebarWidth(900, 230, 620)).toBe(620);
        expect(clampDesignerSidebarWidth(360, 230, 620)).toBe(360);
    });

    it('normalizes invalid values without collapsing the palette', () => {
        expect(clampDesignerSidebarWidth(Number.NaN, 260, 540)).toBe(310);
        expect(clampDesignerSidebarWidth(Number.POSITIVE_INFINITY, 340, 540)).toBe(340);
    });

    it('normalizes inverted bounds defensively', () => {
        expect(clampDesignerSidebarWidth(400, 500, 300)).toBe(500);
    });

    it('rounds fractional widths so persisted layouts are stable', () => {
        expect(clampDesignerSidebarWidth(333.6, 230, 620)).toBe(334);
    });

    it('enforces an absolute readable minimum', () => {
        expect(clampDesignerSidebarWidth(100, 80, 620)).toBe(180);
    });

    it('accepts the minimum boundary', () => {
        expect(clampDesignerSidebarWidth(230, 230, 620)).toBe(230);
    });

    it('accepts the maximum boundary', () => {
        expect(clampDesignerSidebarWidth(620, 230, 620)).toBe(620);
    });

    it('uses the default width for negative infinity', () => {
        expect(clampDesignerSidebarWidth(Number.NEGATIVE_INFINITY, 230, 620)).toBe(310);
    });

    it('keeps custom minimums above the absolute floor', () => {
        expect(clampDesignerSidebarWidth(200, 260, 620)).toBe(260);
    });

    it('raises a custom maximum when it is below the minimum', () => {
        expect(clampDesignerSidebarWidth(900, 500, 320)).toBe(500);
    });
});
