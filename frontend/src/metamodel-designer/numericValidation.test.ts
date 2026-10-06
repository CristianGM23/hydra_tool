import { describe, expect, it } from 'vitest';

import { formatEditableNumber, validateRealNumber } from './numericValidation';

describe('numericValidation', () => {
    it('accepts natural and real numbers written with point or comma', () => {
        expect(validateRealNumber('12')).toEqual({ valid: true, value: 12 });
        expect(validateRealNumber('12.5')).toEqual({ valid: true, value: 12.5 });
        expect(validateRealNumber('12,5')).toEqual({ valid: true, value: 12.5 });
        expect(validateRealNumber('-3.25')).toEqual({ valid: true, value: -3.25 });
    });

    it('rejects empty values text exponential notation and non-finite numbers', () => {
        expect(validateRealNumber('').valid).toBe(false);
        expect(validateRealNumber('abc').valid).toBe(false);
        expect(validateRealNumber('1e3').valid).toBe(false);
        expect(validateRealNumber('Infinity').valid).toBe(false);
        expect(validateRealNumber('12px').valid).toBe(false);
    });

    it('enforces natural-number and range constraints', () => {
        expect(validateRealNumber('4', { integer: true, min: 0, max: 10 })).toEqual({ valid: true, value: 4 });
        expect(validateRealNumber('-1', { integer: true }).valid).toBe(false);
        expect(validateRealNumber('4.2', { integer: true }).valid).toBe(false);
        expect(validateRealNumber('11', { max: 10 }).valid).toBe(false);
        expect(validateRealNumber('2', { min: 3 }).valid).toBe(false);
    });

    it('formats editable numbers without unnecessary decimal noise', () => {
        expect(formatEditableNumber(12.34001, 3)).toBe('12.34');
        expect(formatEditableNumber(Number.POSITIVE_INFINITY)).toBe('0');
    });
});
