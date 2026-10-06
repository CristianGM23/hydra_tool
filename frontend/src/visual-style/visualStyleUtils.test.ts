import { describe, expect, it } from 'vitest';

import { DEFAULT_CLASS_NODE_STYLE, hexToRgb, normalizeHexColor, resolveEdgeStyle, resolveNodeStyle, rgbToHex } from './visualStyleUtils';

/**
 * Comprueba la conversión y resolución de estilos reutilizables.
 */
describe('visual style utilities', () => {
    it('normalizes short and full hexadecimal colors', () => {
        expect(normalizeHexColor('#abc', '')).toBe('#AABBCC');
        expect(normalizeHexColor('12ef90', '')).toBe('#12EF90');
    });

    it('converts between hexadecimal and RGB', () => {
        expect(hexToRgb('#336699')).toEqual({ red: 51, green: 102, blue: 153 });
        expect(rgbToHex(51, 102, 153)).toBe('#336699');
    });

    it('resolves invalid node values with defaults', () => {
        const style = resolveNodeStyle({ fillColor: 'invalid', fontSize: 0 }, DEFAULT_CLASS_NODE_STYLE);
        expect(style.fillColor).toBe(DEFAULT_CLASS_NODE_STYLE.fillColor);
        expect(style.fontSize).toBe(DEFAULT_CLASS_NODE_STYLE.fontSize);
    });

    it('keeps a configured edge width', () => {
        expect(resolveEdgeStyle({ strokeWidth: 5 }).strokeWidth).toBe(5);
    });
});
