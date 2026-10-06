import type { Edge, Node } from '@xyflow/react';

import type { DiagramDocument, DiagramEdgeView, DiagramNodeView } from '../types/editor';
import { measureHydraDiagramOperation } from './performanceTelemetry';

interface VisualData extends Record<string, unknown> {
    label?: string;
    notation?: {
        id?: string;
    };
}

/**
 * Convierte nodos y conexiones de React Flow en un documento persistente.
 *
 * @param modelFileName contiene el nombre del XMI.
 * @param notationId identifica la notación activa.
 * @param nodes contiene los nodos de React Flow.
 * @param edges contiene las conexiones de React Flow.
 * @returns documento visual preparado para guardar.
 */
export function buildDiagramDocument(modelFileName: string, notationId: string, nodes: Node[], edges: Edge[]): DiagramDocument {
    return measureHydraDiagramOperation(`model:${modelFileName}`, 'serialization', () => {
        const diagramNodes = nodes.map(node => createNodeView(node));
        const diagramEdges = edges.map(edge => createEdgeView(edge)).filter(edge => edge.referenceName !== '');
        return { formatVersion: 1, modelFileName, notationId, nodes: diagramNodes, edges: diagramEdges };
    }, { nodeCount: nodes.length, edgeCount: edges.length });
}

/**
 * Convierte un nodo de React Flow en un estado visual.
 *
 * @param node contiene el nodo gráfico.
 * @returns estado visual persistente.
 */
function createNodeView(node: Node): DiagramNodeView {
    const data = node.data as VisualData;
    const width = node.measured?.width ?? node.width ?? readDimension(node.style?.width, 180);
    const height = node.measured?.height ?? node.height ?? readDimension(node.style?.height, 90);
    return { elementId: node.id, nodeStyleId: data.notation?.id ?? '', x: node.position.x, y: node.position.y, width, height };
}

/**
 * Convierte una conexión de React Flow en un estado visual.
 *
 * @param edge contiene la conexión gráfica.
 * @returns estado visual persistente.
 */
function createEdgeView(edge: Edge): DiagramEdgeView {
    const data = edge.data as VisualData | undefined;
    const referenceName = typeof data?.label === 'string' ? data.label : '';
    return { sourceId: edge.source, referenceName, targetId: edge.target, edgeStyleId: data?.notation?.id ?? '' };
}

/**
 * Convierte una dimensión CSS en un número.
 *
 * @param value contiene la dimensión.
 * @param fallback contiene el valor alternativo.
 * @returns dimensión numérica.
 */
function readDimension(value: string | number | undefined, fallback: number): number {
    if (typeof value === 'number' && Number.isFinite(value) && value > 0) {
        return value;
    }

    if (typeof value === 'string') {
        const parsedValue = Number.parseFloat(value);

        if (Number.isFinite(parsedValue) && parsedValue > 0) {
            return parsedValue;
        }
    }

    return fallback;
}