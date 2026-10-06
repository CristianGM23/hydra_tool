import type { Edge, Node } from '@xyflow/react';
import { DEFAULT_EDGE_STYLE, DEFAULT_NODE_STYLE, findEdgeStyle, findEdgeStyleById, findNodeStyleById, findNodeStyles } from '../notation/notationUtils';
import type { DiagramDocument, ModelDocument, ModelElement, NotationDefinition } from '../types/editor';
import type { ValidationSeverity } from '../validation/types';

export interface ModelEditorGraphState {
    nodes: Node[];
    edges: Edge[];
    representations: Record<string, string>;
}

/** Builds the visual projection of a main HYDRA model without mutating React state. */
export function buildModelEditorGraph(
    document: ModelDocument,
    currentNodes: Node[],
    currentRepresentations: Record<string, string>,
    representationOverrides: Record<string, string>,
    diagram: DiagramDocument | null,
    notation: NotationDefinition | null,
    severityByElementId: Record<string, ValidationSeverity>
): ModelEditorGraphState {
    const diagramNodes = new Map(diagram?.nodes.map(node => [node.elementId, node]) ?? []);
    const diagramEdges = new Map(diagram?.edges.map(edge => [modelEdgeKey(edge.sourceId, edge.referenceName, edge.targetId), edge]) ?? []);
    const currentNodeById = new Map(currentNodes.map(node => [node.id, node]));
    const elementById = new Map(document.elements.map(element => [element.id, element]));
    const representations = { ...currentRepresentations, ...representationOverrides };
    for (const diagramNode of diagram?.nodes ?? []) if (diagramNode.nodeStyleId !== '') representations[diagramNode.elementId] = diagramNode.nodeStyleId;

    const nodes = document.elements.map((element, index) => {
        const existingNode = currentNodeById.get(element.id);
        const diagramNode = diagramNodes.get(element.id);
        const selectedStyle = findNodeStyleById(notation, representations[element.id]);
        const nodeStyle = selectedStyle ?? findNodeStyles(notation, element.classId)[0] ?? DEFAULT_NODE_STYLE;
        let position = { x: 80 + index * 45, y: 80 + index * 45 };
        let width = nodeStyle.defaultWidth;
        let height = nodeStyle.defaultHeight;
        if (existingNode !== undefined) {
            position = existingNode.position;
            width = existingNode.measured?.width ?? existingNode.width ?? readModelDimension(existingNode.style?.width, nodeStyle.defaultWidth);
            height = existingNode.measured?.height ?? existingNode.height ?? readModelDimension(existingNode.style?.height, nodeStyle.defaultHeight);
        }
        if (diagramNode !== undefined) { position = { x: diagramNode.x, y: diagramNode.y }; width = diagramNode.width; height = diagramNode.height; }
        return { id: element.id, type: 'semantic', position, style: { ...existingNode?.style, width, height }, data: { label: modelElementLabel(element), notation: nodeStyle, problemSeverity: severityByElementId[element.id] } } satisfies Node;
    });

    const edges = document.references.map((reference, index) => {
        const sourceElement = elementById.get(reference.sourceId);
        const targetElement = elementById.get(reference.targetId);
        const diagramEdge = diagramEdges.get(modelEdgeKey(reference.sourceId, reference.referenceName, reference.targetId));
        let edgeStyle = findEdgeStyleById(notation, diagramEdge?.edgeStyleId ?? '') ?? DEFAULT_EDGE_STYLE;
        if (edgeStyle === DEFAULT_EDGE_STYLE && sourceElement !== undefined && targetElement !== undefined) {
            edgeStyle = findEdgeStyle(notation, sourceElement.classId, reference.referenceName, targetElement.classId) ?? DEFAULT_EDGE_STYLE;
        }
        return { id: `reference-${index}-${reference.sourceId}-${reference.targetId}`, type: 'semantic', source: reference.sourceId, target: reference.targetId, animated: edgeStyle.animated, data: { label: reference.referenceName, notation: edgeStyle } } satisfies Edge;
    });
    return { nodes, edges, representations };
}

export function findModelElement(document: ModelDocument, elementId: string): ModelElement | undefined {
    return document.elements.find(element => element.id === elementId);
}

function readModelDimension(value: string | number | undefined, fallback: number): number {
    if (typeof value === 'number' && Number.isFinite(value) && value > 0) return value;
    if (typeof value === 'string') { const parsed = Number.parseFloat(value); if (Number.isFinite(parsed) && parsed > 0) return parsed; }
    return fallback;
}
function modelEdgeKey(sourceId: string, referenceName: string, targetId: string): string { return `${sourceId}|${referenceName}|${targetId}`; }
function modelElementLabel(element: ModelElement): string { const value = element.attributes.name; return typeof value === 'string' && value !== '' ? value : element.className; }
