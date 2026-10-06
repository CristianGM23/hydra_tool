import type { Node, NodeChange } from '@xyflow/react';

export type DiagramSelectionArrangement = 'ALIGN_LEFT' | 'ALIGN_TOP' | 'DISTRIBUTE_HORIZONTAL' | 'DISTRIBUTE_VERTICAL';

interface NodeSize { width: number; height: number; }

/**
 * Calcula posiciones visuales para una selección sin modificar nodos ajenos.
 */
export function arrangeDiagramSelection<T extends Record<string, unknown>>(nodes: Node<T>[], selectedNodeIds: readonly string[], arrangement: DiagramSelectionArrangement, gap = 48): Node<T>[] {
    const selected = new Set(selectedNodeIds);
    const targets = nodes.filter(node => selected.has(node.id));
    if (targets.length < 2) return nodes;

    const positions = new Map<string, { x: number; y: number }>();
    if (arrangement === 'ALIGN_LEFT') alignVertical(targets, positions, gap);
    else if (arrangement === 'ALIGN_TOP') alignHorizontal(targets, positions, gap);
    else distribute(targets, positions, arrangement === 'DISTRIBUTE_HORIZONTAL', gap);

    return nodes.map(node => positions.has(node.id) ? { ...node, position: positions.get(node.id)!, selected: true } : node);
}

/** Convierte el resultado de una distribución en cambios estándar de React Flow. */
export function positionChangesForSelection<T extends Record<string, unknown>>(before: Node<T>[], after: Node<T>[], selectedNodeIds: readonly string[]): NodeChange<Node<T>>[] {
    const selected = new Set(selectedNodeIds);
    const previous = new Map(before.map(node => [node.id, node.position]));
    return after.filter(node => selected.has(node.id)).filter(node => {
        const old = previous.get(node.id);
        return old !== undefined && (old.x !== node.position.x || old.y !== node.position.y);
    }).map(node => ({ id: node.id, type: 'position', position: node.position, dragging: false }));
}

function alignVertical<T extends Record<string, unknown>>(nodes: Node<T>[], positions: Map<string, { x: number; y: number }>, gap: number): void {
    const ordered = [...nodes].sort((a, b) => a.position.y - b.position.y || a.position.x - b.position.x || a.id.localeCompare(b.id));
    const left = Math.min(...ordered.map(node => node.position.x));
    let y = Math.min(...ordered.map(node => node.position.y));
    for (const node of ordered) { positions.set(node.id, { x: left, y }); y += nodeSize(node).height + gap; }
}

function alignHorizontal<T extends Record<string, unknown>>(nodes: Node<T>[], positions: Map<string, { x: number; y: number }>, gap: number): void {
    const ordered = [...nodes].sort((a, b) => a.position.x - b.position.x || a.position.y - b.position.y || a.id.localeCompare(b.id));
    const top = Math.min(...ordered.map(node => node.position.y));
    let x = Math.min(...ordered.map(node => node.position.x));
    for (const node of ordered) { positions.set(node.id, { x, y: top }); x += nodeSize(node).width + gap; }
}

function distribute<T extends Record<string, unknown>>(nodes: Node<T>[], positions: Map<string, { x: number; y: number }>, horizontal: boolean, minimumGap: number): void {
    if (nodes.length < 3) return;
    const ordered = [...nodes].sort((a, b) => horizontal ? a.position.x - b.position.x || a.id.localeCompare(b.id) : a.position.y - b.position.y || a.id.localeCompare(b.id));
    const first = ordered[0];
    const last = ordered[ordered.length - 1];
    const firstStart = horizontal ? first.position.x : first.position.y;
    const lastEnd = horizontal ? last.position.x + nodeSize(last).width : last.position.y + nodeSize(last).height;
    const total = ordered.reduce((sum, node) => sum + (horizontal ? nodeSize(node).width : nodeSize(node).height), 0);
    const gap = Math.max(minimumGap, (lastEnd - firstStart - total) / (ordered.length - 1));
    let cursor = firstStart;
    for (const node of ordered) {
        positions.set(node.id, horizontal ? { x: cursor, y: node.position.y } : { x: node.position.x, y: cursor });
        cursor += (horizontal ? nodeSize(node).width : nodeSize(node).height) + gap;
    }
}

function nodeSize<T extends Record<string, unknown>>(node: Node<T>): NodeSize {
    return {
        width: node.measured?.width ?? node.width ?? 190,
        height: node.measured?.height ?? node.height ?? 84
    };
}
