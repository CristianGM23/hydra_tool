import { applyNodeChanges, useNodesState } from '@xyflow/react';
import type { Node, NodeChange, OnNodesChange } from '@xyflow/react';
import { useCallback, useEffect, useRef } from 'react';

/**
 * Mirrors the Model Editor interaction contract for secondary React Flow canvases:
 * React Flow owns transient drag/resize geometry; the domain document is updated
 * only once the interaction is committed. This prevents render/persist feedback
 * loops while keeping every canvas responsive to the pointer.
 */
export function isHydraTransientGeometryChange<NodeType extends Node>(change: NodeChange<NodeType>): boolean {
    if (change.type === 'dimensions') return 'resizing' in change && change.resizing === true;
    if (change.type === 'position') return 'dragging' in change && change.dragging === true;
    return false;
}

export function hasHydraCommittedGeometryChange<NodeType extends Node>(changes: NodeChange<NodeType>[]): boolean {
    return changes.some(change => (change.type === 'dimensions' || change.type === 'position') && !isHydraTransientGeometryChange(change));
}

function interactionKey<NodeType extends Node>(change: NodeChange<NodeType>): string {
    return `${'id' in change ? change.id : 'document'}:${change.type}`;
}

function hasActiveInteractionForNode(activeInteractions: ReadonlySet<string>, nodeId: string): boolean {
    return activeInteractions.has(`${nodeId}:dimensions`) || activeInteractions.has(`${nodeId}:position`);
}

/**
 * React parents frequently rebuild the `nodes` array while preserving the same
 * immutable node objects. Treat that as the same external snapshot so the
 * synchronization effect does not feed its own local `setNodes` render back
 * into another synchronization cycle.
 */
export function hasSameHydraNodeReferences<NodeType extends Node>(next: readonly NodeType[], previous: readonly NodeType[]): boolean {
    return next.length === previous.length && next.every((node, index) => node === previous[index]);
}

function sameHydraNodeValue(left: unknown, right: unknown): boolean {
    if (Object.is(left, right)) return true;
    if (left === null || right === null || typeof left !== typeof right) return false;
    if (Array.isArray(left) || Array.isArray(right)) {
        if (!Array.isArray(left) || !Array.isArray(right) || left.length !== right.length) return false;
        return left.every((value, index) => sameHydraNodeValue(value, right[index]));
    }
    if (typeof left !== 'object') return false;
    const leftRecord = left as Record<string, unknown>;
    const rightRecord = right as Record<string, unknown>;
    const leftKeys = Object.keys(leftRecord);
    const rightKeys = Object.keys(rightRecord);
    if (leftKeys.length !== rightKeys.length) return false;
    return leftKeys.every(key => Object.prototype.hasOwnProperty.call(rightRecord, key) && sameHydraNodeValue(leftRecord[key], rightRecord[key]));
}

/**
 * React Flow adds local-only fields such as `measured`, `dragging` and `resizing`.
 * Compare the authoritative external projection without forcing those transient
 * fields back through StoreUpdater. This lets a freshly materialized but
 * semantically identical HYDRA node reuse the already-mounted React Flow node.
 */
export function hasSameHydraExternalNodeState<NodeType extends Node>(external: NodeType, local: NodeType): boolean {
    const externalRecord = external as unknown as Record<string, unknown>;
    const localRecord = local as unknown as Record<string, unknown>;
    return Object.keys(externalRecord).every(key => {
        if (key === 'measured' || key === 'dragging' || key === 'resizing') return true;
        return sameHydraNodeValue(externalRecord[key], localRecord[key]);
    });
}

export function isHydraNoOpNodeChange<NodeType extends Node>(change: NodeChange<NodeType>, node: NodeType | undefined): boolean {
    if (node === undefined || !('id' in change)) return false;
    if (change.type === 'select') return Boolean(node.selected) === change.selected;
    if (change.type === 'position') {
        const samePosition = change.position === undefined || (node.position.x === change.position.x && node.position.y === change.position.y);
        const sameDragging = change.dragging === undefined || Boolean(node.dragging) === change.dragging;
        return samePosition && sameDragging;
    }
    if (change.type === 'dimensions') {
        const width = node.measured?.width;
        const height = node.measured?.height;
        const sameDimensions = change.dimensions === undefined || (width === change.dimensions.width && height === change.dimensions.height);
        const sameResizing = change.resizing === undefined || Boolean(node.resizing) === change.resizing;
        const writesWidth = change.setAttributes === true || change.setAttributes === 'width';
        const writesHeight = change.setAttributes === true || change.setAttributes === 'height';
        const sameWidthAttribute = !writesWidth || change.dimensions === undefined || node.width === change.dimensions.width;
        const sameHeightAttribute = !writesHeight || change.dimensions === undefined || node.height === change.dimensions.height;
        return sameDimensions && sameResizing && sameWidthAttribute && sameHeightAttribute;
    }
    return false;
}

/**
 * External semantic changes must never disappear just because another node is in
 * a transient drag/resize frame. Preserve local geometry only for the node being
 * manipulated and keep React Flow's measured size when the canonical node itself
 * has not changed size.
 */
export function reconcileHydraExternalNodes<NodeType extends Node>(externalNodes: NodeType[], localNodes: NodeType[], activeInteractions: ReadonlySet<string>): NodeType[] {
    const localById = new Map(localNodes.map(node => [node.id, node] as const));
    return externalNodes.map(external => {
        const local = localById.get(external.id);
        if (local === undefined) return external;
        if (hasActiveInteractionForNode(activeInteractions, external.id)) {
            const reconciled = { ...external, position: local.position, measured: local.measured } as NodeType;
            return hasSameHydraExternalNodeState(reconciled, local) ? local : reconciled;
        }
        if (hasSameHydraExternalNodeState(external, local)) return local;
        const sameCanonicalSize = external.style?.width === local.style?.width && external.style?.height === local.style?.height;
        return sameCanonicalSize && local.measured !== undefined ? { ...external, measured: local.measured } as NodeType : external;
    });
}

function finalizedGeometryChange<NodeType extends Node>(change: NodeChange<NodeType>, nodesById: ReadonlyMap<string,NodeType>): NodeChange<NodeType> {
    if (!('id' in change)) return change;
    const node = nodesById.get(change.id);
    if (node === undefined) return change;
    if (change.type === 'position') return { ...change, position: node.position, dragging: false } as NodeChange<NodeType>;
    if (change.type === 'dimensions') {
        const style = node.style ?? {};
        const width = node.measured?.width ?? node.width ?? numberStyle(style.width);
        const height = node.measured?.height ?? node.height ?? numberStyle(style.height);
        if (width !== undefined && height !== undefined) return { ...change, dimensions:{ width, height }, resizing:false } as NodeChange<NodeType>;
    }
    return change;
}

function numberStyle(value: unknown): number | undefined {
    if (typeof value === 'number' && Number.isFinite(value)) return value;
    if (typeof value === 'string') { const parsed = Number.parseFloat(value); if (Number.isFinite(parsed)) return parsed; }
    return undefined;
}

export function useHydraModelEditorNodeResize<NodeType extends Node>(externalNodes: NodeType[], onCommittedChanges: OnNodesChange<NodeType>): { nodes:NodeType[]; onNodesChange:OnNodesChange<NodeType> } {
    const [nodes, setNodes] = useNodesState<NodeType>(externalNodes);
    const nodesRef = useRef<NodeType[]>(externalNodes);
    const lastExternalNodesRef = useRef<readonly NodeType[]>(externalNodes);
    const interactions = useRef(new Set<string>());

    useEffect(() => {
        const previousExternalNodes = lastExternalNodesRef.current;
        lastExternalNodesRef.current = externalNodes;
        if (hasSameHydraNodeReferences(externalNodes, previousExternalNodes)) return;
        const externalIds = new Set(externalNodes.map(node => node.id));
        for (const key of interactions.current) {
            const separator = key.lastIndexOf(':');
            if (separator > 0 && !externalIds.has(key.slice(0, separator))) interactions.current.delete(key);
        }
        const reconciled = reconcileHydraExternalNodes(externalNodes, nodesRef.current, interactions.current);
        if (hasSameHydraNodeReferences(reconciled, nodesRef.current)) return;
        nodesRef.current = reconciled;
        setNodes(reconciled);
    }, [externalNodes, setNodes]);

    const onNodesChange = useCallback<OnNodesChange<NodeType>>((changes: NodeChange<NodeType>[]) => {
        const currentNodes = nodesRef.current;
        const currentById = new Map(currentNodes.map(node => [node.id, node] as const));
        const effectiveChanges = changes.filter(change => !isHydraNoOpNodeChange(change, 'id' in change ? currentById.get(change.id) : undefined));
        if (effectiveChanges.length === 0) return;
        const nextNodes = applyNodeChanges(effectiveChanges, currentNodes) as NodeType[];
        nodesRef.current = nextNodes;
        setNodes(nextNodes);
        const committed: NodeChange<NodeType>[] = [];
        const nodesById = new Map(nextNodes.map(node => [node.id,node] as const));
        for (const change of effectiveChanges) {
            if (change.type !== 'dimensions' && change.type !== 'position') {
                if (change.type === 'remove') {
                    interactions.current.delete(`${change.id}:dimensions`);
                    interactions.current.delete(`${change.id}:position`);
                }
                committed.push(change);
                continue;
            }
            const key = interactionKey(change);
            if (isHydraTransientGeometryChange(change)) { interactions.current.add(key); continue; }
            const completesInteraction = interactions.current.delete(key);
            // React Flow emits dimension changes while measuring freshly mounted
            // nodes. They are view-state, not user resizes, and must not rebuild
            // the canonical document. Only persist a dimensions frame that closes
            // a resize interaction started with resizing=true.
            if (change.type === 'dimensions' && !completesInteraction) continue;
            committed.push(finalizedGeometryChange(change, nodesById));
        }
        if (committed.length > 0) onCommittedChanges(committed);
    }, [onCommittedChanges, setNodes]);

    return { nodes, onNodesChange };
}
