import type { EdgeMarkerType, EdgeNotationStyle, NodeNotationStyle, NotationDefinition } from '../types/editor';

/**
 * Estilo utilizado cuando no existe una notación específica.
 */
export const DEFAULT_NODE_STYLE: NodeNotationStyle = {
    id: 'default-node',
    classId: '*',
    representationName: 'Default',
    shape: 'ROUNDED_RECTANGLE',
    defaultWidth: 180,
    defaultHeight: 90,
    minWidth: 100,
    minHeight: 50,
    fillColor: '#ffffff',
    strokeColor: '#1f2937',
    strokeWidth: 2,
    strokeDash: '',
    borderRadius: 8,
    labelAttribute: 'name',
    svgViewBox: '',
    svgPath: ''
};

/**
 * Estilo utilizado cuando no existe una notación de referencia.
 */
export const DEFAULT_EDGE_STYLE: EdgeNotationStyle = {
    id: 'default-edge',
    sourceClassId: '*',
    referenceName: '*',
    targetClassId: '*',
    lineType: 'BEZIER',
    strokeColor: '#334155',
    strokeWidth: 2,
    strokeDash: '',
    startMarker: 'NONE',
    endMarker: 'ARROW_CLOSED',
    animated: false
};

/**
 * Obtiene todas las representaciones de una clase.
 *
 * @param notation contiene la notación.
 * @param classId identifica la clase.
 * @returns representaciones encontradas.
 */
export function findNodeStyles(notation: NotationDefinition | null, classId: string): NodeNotationStyle[] {
    if (notation === null) {
        return [];
    }

    return notation.nodeStyles.filter(style => style.classId === classId || style.classId === '*');
}

/**
 * Busca una representación por identificador.
 *
 * @param notation contiene la notación.
 * @param styleId identifica el estilo.
 * @returns estilo encontrado.
 */
export function findNodeStyleById(notation: NotationDefinition | null, styleId: string): NodeNotationStyle | undefined {
    if (notation === null || styleId === '') {
        return undefined;
    }

    return notation.nodeStyles.find(style => style.id === styleId);
}

/**
 * Busca el estilo de una referencia.
 *
 * @param notation contiene la notación.
 * @param sourceClassId identifica la clase de origen.
 * @param referenceName identifica la referencia.
 * @param targetClassId identifica la clase de destino.
 * @returns estilo encontrado.
 */
export function findEdgeStyle(notation: NotationDefinition | null, sourceClassId: string, referenceName: string, targetClassId: string): EdgeNotationStyle | undefined {
    if (notation === null) {
        return undefined;
    }

    return notation.edgeStyles.find(style => {
        const sourceMatches = style.sourceClassId === sourceClassId || style.sourceClassId === '*';
        const referenceMatches = style.referenceName === referenceName || style.referenceName === '*';
        const targetMatches = style.targetClassId === targetClassId || style.targetClassId === '*';
        return sourceMatches && referenceMatches && targetMatches;
    });
}

/**
 * Construye la referencia SVG de un marcador.
 *
 * @param marker contiene el tipo de marcador.
 * @returns referencia SVG o undefined.
 */
export function getMarkerUrl(marker: EdgeMarkerType): string | undefined {
    if (marker === 'NONE') {
        return undefined;
    }

    return `url(#marker-${marker.toLowerCase().replaceAll('_', '-')})`;
}

/**
 * Busca un estilo de conexión mediante su identificador.
 *
 * @param notation contiene la notación.
 * @param styleId identifica el estilo.
 * @returns estilo encontrado.
 */
export function findEdgeStyleById(notation: NotationDefinition | null, styleId: string): EdgeNotationStyle | undefined {
    if (notation === null || styleId === '') {
        return undefined;
    }

    return notation.edgeStyles.find(style => style.id === styleId);
}