import type { VisualEdgeStyle, VisualNodeStyle } from './visualStyleTypes';

/**
 * Estilo predeterminado de una clase.
 */
export const DEFAULT_CLASS_NODE_STYLE: VisualNodeStyle = { fillColor: '#ffffff', headerColor: '#e2e8f0', strokeColor: '#475569', textColor: '#0f172a', fontFamily: 'Arial', fontSize: 12, fontWeight: 600, strokeWidth: 2, borderRadius: 4 };

/**
 * Estilo predeterminado de una enumeración.
 */
export const DEFAULT_ENUM_NODE_STYLE: VisualNodeStyle = { fillColor: '#ffffff', headerColor: '#ede9fe', strokeColor: '#7c3aed', textColor: '#0f172a', fontFamily: 'Arial', fontSize: 12, fontWeight: 600, strokeWidth: 2, borderRadius: 4 };

/**
 * Estilo predeterminado de un tipo de datos.
 */
export const DEFAULT_DATA_TYPE_NODE_STYLE: VisualNodeStyle = { fillColor: '#fffbeb', headerColor: '#fef3c7', strokeColor: '#b45309', textColor: '#451a03', fontFamily: 'Arial', fontSize: 12, fontWeight: 600, strokeWidth: 2, borderRadius: 4 };

/**
 * Estilo predeterminado de una relación.
 */
export const DEFAULT_EDGE_STYLE: VisualEdgeStyle = { strokeColor: '#334155', textColor: '#334155', fontFamily: 'Arial', fontSize: 10, fontWeight: 600, strokeWidth: 2.25, lineStyle: 'AUTO' };

/**
 * Combina un estilo parcial con un estilo predeterminado de nodo.
 *
 * @param value contiene el estilo parcial.
 * @param fallback contiene el estilo predeterminado.
 * @returns estilo completo.
 */
export function resolveNodeStyle(value: Partial<VisualNodeStyle> | undefined, fallback: VisualNodeStyle): VisualNodeStyle {
    return {
        fillColor: normalizeHexColor(value?.fillColor, fallback.fillColor),
        headerColor: normalizeHexColor(value?.headerColor, fallback.headerColor),
        strokeColor: normalizeHexColor(value?.strokeColor, fallback.strokeColor),
        textColor: normalizeHexColor(value?.textColor, fallback.textColor),
        fontFamily: readText(value?.fontFamily, fallback.fontFamily),
        fontSize: readPositiveNumber(value?.fontSize, fallback.fontSize),
        fontWeight: readPositiveNumber(value?.fontWeight, fallback.fontWeight),
        strokeWidth: readPositiveNumber(value?.strokeWidth, fallback.strokeWidth),
        borderRadius: readNonNegativeNumber(value?.borderRadius, fallback.borderRadius)
    };
}

/**
 * Combina un estilo parcial con el estilo predeterminado de relación.
 *
 * @param value contiene el estilo parcial.
 * @returns estilo completo.
 */
export function resolveEdgeStyle(value: Partial<VisualEdgeStyle> | undefined): VisualEdgeStyle {
    const lineStyle = value?.lineStyle;
    let resolvedLineStyle: VisualEdgeStyle['lineStyle'] = DEFAULT_EDGE_STYLE.lineStyle;

    if (lineStyle === 'SOLID' || lineStyle === 'DASHED' || lineStyle === 'DOTTED' || lineStyle === 'AUTO') {
        resolvedLineStyle = lineStyle;
    }

    return {
        strokeColor: normalizeHexColor(value?.strokeColor, DEFAULT_EDGE_STYLE.strokeColor),
        textColor: normalizeHexColor(value?.textColor, DEFAULT_EDGE_STYLE.textColor),
        fontFamily: readText(value?.fontFamily, DEFAULT_EDGE_STYLE.fontFamily),
        fontSize: readPositiveNumber(value?.fontSize, DEFAULT_EDGE_STYLE.fontSize),
        fontWeight: readPositiveNumber(value?.fontWeight, DEFAULT_EDGE_STYLE.fontWeight),
        strokeWidth: readPositiveNumber(value?.strokeWidth, DEFAULT_EDGE_STYLE.strokeWidth),
        lineStyle: resolvedLineStyle
    };
}

/**
 * Convierte un color hexadecimal a RGB.
 *
 * @param value contiene el color hexadecimal.
 * @returns componentes RGB o null.
 */
export function hexToRgb(value: string): { red: number; green: number; blue: number } | null {
    const normalized = normalizeHexColor(value, '');

    if (normalized === '') {
        return null;
    }

    return { red: Number.parseInt(normalized.substring(1, 3), 16), green: Number.parseInt(normalized.substring(3, 5), 16), blue: Number.parseInt(normalized.substring(5, 7), 16) };
}

/**
 * Convierte componentes RGB en hexadecimal.
 *
 * @param red contiene el componente rojo.
 * @param green contiene el componente verde.
 * @param blue contiene el componente azul.
 * @returns color hexadecimal.
 */
export function rgbToHex(red: number, green: number, blue: number): string {
    return `#${componentToHex(red)}${componentToHex(green)}${componentToHex(blue)}`;
}

/**
 * Normaliza un color hexadecimal.
 *
 * @param value contiene el color.
 * @param fallback contiene el valor alternativo.
 * @returns color con formato #RRGGBB.
 */
export function normalizeHexColor(value: string | undefined, fallback: string): string {
    if (value === undefined) {
        return fallback;
    }

    const trimmed = value.trim();
    const shortMatch = /^#?([0-9a-fA-F]{3})$/.exec(trimmed);

    if (shortMatch !== null) {
        const digits = shortMatch[1];
        return `#${digits[0]}${digits[0]}${digits[1]}${digits[1]}${digits[2]}${digits[2]}`.toUpperCase();
    }

    const fullMatch = /^#?([0-9a-fA-F]{6})$/.exec(trimmed);

    if (fullMatch !== null) {
        return `#${fullMatch[1]}`.toUpperCase();
    }

    return fallback;
}

/**
 * Devuelve el patrón SVG de una línea personalizada.
 *
 * @param lineStyle contiene el estilo configurado.
 * @param semanticDash contiene el patrón semántico predeterminado.
 * @returns patrón SVG o undefined.
 */
export function resolveStrokeDasharray(lineStyle: VisualEdgeStyle['lineStyle'], semanticDash: string | undefined): string | undefined {
    if (lineStyle === 'SOLID') {
        return undefined;
    }

    if (lineStyle === 'DASHED') {
        return '8 5';
    }

    if (lineStyle === 'DOTTED') {
        return '2 5';
    }

    return semanticDash;
}

/**
 * Convierte un componente RGB en hexadecimal.
 *
 * @param value contiene el componente.
 * @returns dos dígitos hexadecimales.
 */
function componentToHex(value: number): string {
    const limited = Math.min(255, Math.max(0, Math.round(value)));
    return limited.toString(16).padStart(2, '0').toUpperCase();
}

/**
 * Devuelve un número positivo.
 *
 * @param value contiene el valor.
 * @param fallback contiene el valor alternativo.
 * @returns número válido.
 */
function readPositiveNumber(value: number | undefined, fallback: number): number {
    if (value !== undefined && Number.isFinite(value) && value > 0) {
        return value;
    }

    return fallback;
}

/**
 * Devuelve un número no negativo.
 *
 * @param value contiene el valor.
 * @param fallback contiene el valor alternativo.
 * @returns número válido.
 */
function readNonNegativeNumber(value: number | undefined, fallback: number): number {
    if (value !== undefined && Number.isFinite(value) && value >= 0) {
        return value;
    }

    return fallback;
}

/**
 * Devuelve un texto no vacío.
 *
 * @param value contiene el texto.
 * @param fallback contiene el valor alternativo.
 * @returns texto válido.
 */
function readText(value: string | undefined, fallback: string): string {
    if (value !== undefined && value.trim() !== '') {
        return value;
    }

    return fallback;
}
