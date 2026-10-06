/** Número de píxeles CSS que equivalen a un centímetro a 96 ppp. */
export const PIXELS_PER_CENTIMETER = 96 / 2.54;

/** Unidad disponible en el editor de tamaños. */
export type MetamodelSizeUnit = 'PX' | 'CM';

/**
 * Convierte una dimensión almacenada en píxeles a la unidad elegida.
 */
export function pixelsToUnit(valueInPixels: number, unit: MetamodelSizeUnit): number {
    if(unit === 'CM') {
        return valueInPixels / PIXELS_PER_CENTIMETER;
    }

    return valueInPixels;
}

/**
 * Convierte una dimensión escrita por el usuario a píxeles, que es la unidad persistida por el diagrama.
 */
export function unitToPixels(value: number, unit: MetamodelSizeUnit): number {
    if(unit === 'CM') {
        return value * PIXELS_PER_CENTIMETER;
    }

    return value;
}
