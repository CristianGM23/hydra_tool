import { describe, expect, it } from 'vitest';

import { findEdgeStyle, findNodeStyles, getMarkerUrl } from './notationUtils';
import type { NotationDefinition } from '../types/editor';

/**
 * Notación utilizada para comprobar representaciones y conexiones.
 */
const notation: NotationDefinition = {
    id: 'introspection-default',
    metamodelId: 'introspection',
    name: 'Introspection default',
    nodeStyles: [
        {
            id: 'node-card',
            classId: 'https://example.com/introspection#//Node',
            representationName: 'Card',
            shape: 'ROUNDED_RECTANGLE',
            defaultWidth: 180,
            defaultHeight: 90,
            minWidth: 120,
            minHeight: 60,
            fillColor: '#eff6ff',
            strokeColor: '#2563eb',
            strokeWidth: 2,
            strokeDash: '',
            borderRadius: 10,
            labelAttribute: 'name',
            svgViewBox: '',
            svgPath: ''
        },
        {
            id: 'node-state',
            classId: 'https://example.com/introspection#//Node',
            representationName: 'State',
            shape: 'ELLIPSE',
            defaultWidth: 150,
            defaultHeight: 90,
            minWidth: 90,
            minHeight: 60,
            fillColor: '#f0fdf4',
            strokeColor: '#16a34a',
            strokeWidth: 3,
            strokeDash: '8 4',
            borderRadius: 0,
            labelAttribute: 'name',
            svgViewBox: '',
            svgPath: ''
        }
    ],
    edgeStyles: [
        {
            id: 'node-target',
            sourceClassId: 'https://example.com/introspection#//Node',
            referenceName: 'target',
            targetClassId: 'https://example.com/introspection#//Node',
            lineType: 'BEZIER',
            strokeColor: '#334155',
            strokeWidth: 2,
            strokeDash: '',
            startMarker: 'NONE',
            endMarker: 'ARROW_CLOSED',
            animated: false
        }
    ]
};

/**
 * Comprueba las utilidades utilizadas para resolver estilos declarativos.
 */
describe('notationUtils', () => {
    /**
     * Descripción:
     * Comprueba que una clase puede tener varias representaciones.
     *
     * Entrada:
     * No recibe parámetros.
     *
     * Salida:
     * El test finaliza cuando se encuentran las dos representaciones.
     */
    it('returns multiple representations for one EClass', () => {
        const result = findNodeStyles(notation, 'https://example.com/introspection#//Node');
        expect(result.map(style => style.id)).toEqual(['node-card', 'node-state']);
    });

    /**
     * Descripción:
     * Comprueba que se obtiene el estilo de una referencia concreta.
     *
     * Entrada:
     * No recibe parámetros.
     *
     * Salida:
     * El test finaliza cuando se encuentra el marcador esperado.
     */
    it('selects an edge style by reference', () => {
        const result = findEdgeStyle(notation, 'https://example.com/introspection#//Node', 'target', 'https://example.com/introspection#//Node');
        expect(result?.endMarker).toBe('ARROW_CLOSED');
    });

    /**
     * Descripción:
     * Comprueba la construcción de identificadores SVG para marcadores.
     *
     * Entrada:
     * No recibe parámetros.
     *
     * Salida:
     * El test finaliza cuando las URL son correctas.
     */
    it('creates UML marker URLs', () => {
        expect(getMarkerUrl('DIAMOND_OPEN')).toBe('url(#marker-diamond-open)');
        expect(getMarkerUrl('NONE')).toBeUndefined();
    });
});