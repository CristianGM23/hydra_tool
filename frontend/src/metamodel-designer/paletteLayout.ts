/**
 * Limita la anchura ajustable de la paleta sin depender del componente React.
 *
 * @param width contiene la anchura solicitada.
 * @returns anchura permitida.
 */
export function limitPaletteWidth(width: number): number {
    return Math.min(420, Math.max(190, width));
}
