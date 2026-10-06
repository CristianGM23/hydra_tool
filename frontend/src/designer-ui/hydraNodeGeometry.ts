import type { Node, NodeChange } from '@xyflow/react';

/** Geometry changes persisted by every HYDRA React Flow canvas. */
export function hydraGeometryChanges<NodeType extends Node>(changes: NodeChange<NodeType>[]): NodeChange<NodeType>[] {
    return changes.filter(change => change.type === 'position' || change.type === 'dimensions');
}

/** True when React Flow changed user-controlled node geometry. */
export function hasHydraGeometryChange<NodeType extends Node>(changes: NodeChange<NodeType>[]): boolean {
    return changes.some(change => change.type === 'position' || change.type === 'dimensions');
}

/** Reads the effective node size after dragging a shared HYDRA resize handle. */
export function readHydraNodeSize(node: Node, fallbackWidth: number, fallbackHeight: number): { width:number; height:number } {
    return {
        width: node.measured?.width ?? node.width ?? readStyleSize(node.style?.width) ?? fallbackWidth,
        height: node.measured?.height ?? node.height ?? readStyleSize(node.style?.height) ?? fallbackHeight
    };
}

function readStyleSize(value: unknown): number | undefined {
    if (typeof value === 'number' && Number.isFinite(value)) return value;
    if (typeof value !== 'string') return undefined;
    const parsed = Number.parseFloat(value);
    return Number.isFinite(parsed) ? parsed : undefined;
}
