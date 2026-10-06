import { describe, expect, it } from 'vitest';

import { limitPaletteWidth } from './paletteLayout';

/**
 * Comprueba los límites de la paleta ajustable.
 */
describe('resizable metamodel palette', () => {

    /**
     * Comprueba la anchura mínima.
     */
    it('keeps enough width for section headings', () => {
        expect(limitPaletteWidth(80)).toBe(190);
    });

    /**
     * Comprueba la anchura máxima.
     */
    it('prevents the palette from covering the canvas', () => {
        expect(limitPaletteWidth(700)).toBe(420);
    });

    /**
     * Comprueba un valor intermedio.
     */
    it('keeps an allowed palette width', () => {
        expect(limitPaletteWidth(280)).toBe(280);
    });
});
