import { describe, expect, it } from 'vitest';

import { PIXELS_PER_CENTIMETER, pixelsToUnit, unitToPixels } from './nodeSizeUtils';

describe('nodeSizeUtils', () => {
    it('keeps pixel values unchanged', () => {
        expect(pixelsToUnit(240, 'PX')).toBe(240);
        expect(unitToPixels(240, 'PX')).toBe(240);
    });

    it('converts centimeters using the CSS 96 dpi convention', () => {
        expect(unitToPixels(1, 'CM')).toBeCloseTo(PIXELS_PER_CENTIMETER, 8);
        expect(pixelsToUnit(PIXELS_PER_CENTIMETER * 2.5, 'CM')).toBeCloseTo(2.5, 8);
    });

    it('round trips dimensions without meaningful loss', () => {
        const centimeters = pixelsToUnit(317.25, 'CM');
        expect(unitToPixels(centimeters, 'CM')).toBeCloseTo(317.25, 8);
    });
});
