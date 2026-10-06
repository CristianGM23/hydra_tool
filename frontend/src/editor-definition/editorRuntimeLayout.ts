import type { Node } from '@xyflow/react';
import { arrangeDiagramSelection, type DiagramSelectionArrangement } from '../designer-ui/diagramSelection';
import { layoutHydraElkStyle } from '../diagram/elkStyleLayout';
import type { EditorRenderedEdge, EditorSessionDiagramNodeView } from './types';

export interface RuntimeLayoutNodeData extends Record<string, unknown> { parentId?: string; }

/** Normaliza cualquier giro a un ángulo estable del intervalo [0, 360). */
export function normalizeRuntimeRotation(value: number | undefined): number {
    if (value === undefined || !Number.isFinite(value)) return 0;
    const normalized = value % 360;
    return normalized < 0 ? normalized + 360 : normalized;
}

/** Aplica un giro relativo sin perder las posiciones o dimensiones persistidas. */
export function rotateRuntimeDiagramNodes(nodes: readonly EditorSessionDiagramNodeView[], selectedIds: readonly string[], deltaDegrees: number): EditorSessionDiagramNodeView[] {
    if (!Number.isFinite(deltaDegrees) || deltaDegrees === 0 || selectedIds.length === 0) return [...nodes];
    const selected = new Set(selectedIds);
    return nodes.map(node => selected.has(node.elementId)
        ? { ...node, rotationDegrees: normalizeRuntimeRotation((node.rotationDegrees ?? 0) + deltaDegrees) }
        : node);
}

/**
 * Escala de seguridad para que una figura rotada no invada el área visual de
 * otra representación. El marco de React Flow permanece estable y los handles
 * no cambian de posición.
 */
export function rotationFitScale(width: number, height: number, rotationDegrees: number | undefined): number {
    if (!(width > 0) || !(height > 0)) return 1;
    const radians = normalizeRuntimeRotation(rotationDegrees) * Math.PI / 180;
    const cosine = Math.abs(Math.cos(radians));
    const sine = Math.abs(Math.sin(radians));
    const rotatedWidth = width * cosine + height * sine;
    const rotatedHeight = width * sine + height * cosine;
    if (!(rotatedWidth > 0) || !(rotatedHeight > 0)) return 1;
    return Math.min(1, width / rotatedWidth, height / rotatedHeight);
}

/**
 * Reutiliza las operaciones comunes de alineación/distribución, pero mantiene
 * independientes los elementos que pertenecen a contenedores diferentes.
 */
export function arrangeRuntimeSelection<T extends RuntimeLayoutNodeData>(nodes: Node<T>[], selectedIds: readonly string[], arrangement: DiagramSelectionArrangement): Node<T>[] {
    const groups = groupSelectedByParent(nodes, selectedIds);
    let result = nodes;
    for (const ids of groups.values()) result = arrangeDiagramSelection(result, ids, arrangement, 48);
    return result;
}

/**
 * Aplica el motor ELK-style ya compartido por HYDRA únicamente sobre la
 * selección. La selección conserva el anclaje superior-izquierdo de cada
 * contenedor para evitar saltos innecesarios del resto del diagrama.
 */
export function elkRuntimeSelection<T extends RuntimeLayoutNodeData>(nodes: Node<T>[], edges: readonly EditorRenderedEdge[], selectedIds: readonly string[]): Node<T>[] {
    const selected = new Set(selectedIds);
    const groups = groupSelectedByParent(nodes, selectedIds);
    let result = nodes;
    for (const ids of groups.values()) {
        if (ids.length < 2) continue;
        const groupSet = new Set(ids);
        const groupNodes = result.filter(node => groupSet.has(node.id));
        const anchorX = Math.min(...groupNodes.map(node => node.position.x));
        const anchorY = Math.min(...groupNodes.map(node => node.position.y));
        const placements = layoutHydraElkStyle(
            groupNodes.map(node => ({ id: node.id, width: node.measured?.width ?? node.width ?? 180, height: node.measured?.height ?? node.height ?? 96 })),
            edges.filter(edge => selected.has(edge.sourceElementId) && selected.has(edge.targetElementId) && groupSet.has(edge.sourceElementId) && groupSet.has(edge.targetElementId))
                .map(edge => ({ id: edge.relationInstanceId, source: edge.sourceElementId, target: edge.targetElementId })),
            { direction: 'RIGHT', nodeGap: 56, layerGap: 120, padding: 0 }
        );
        result = result.map(node => {
            if (!groupSet.has(node.id)) return node;
            const placement = placements.get(node.id);
            return placement === undefined ? node : { ...node, position: { x: anchorX + placement.x, y: anchorY + placement.y } };
        });
    }
    return result;
}

function groupSelectedByParent<T extends RuntimeLayoutNodeData>(nodes: Node<T>[], selectedIds: readonly string[]): Map<string, string[]> {
    const selected = new Set(selectedIds);
    const groups = new Map<string,string[]>();
    for (const node of nodes) {
        if (!selected.has(node.id)) continue;
        const key = node.parentId ?? '';
        groups.set(key, [...(groups.get(key) ?? []), node.id]);
    }
    return groups;
}
