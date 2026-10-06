import { act, renderHook, waitFor } from '@testing-library/react';
import type { Node, NodeChange } from '@xyflow/react';
import { describe, expect, it, vi } from 'vitest';
import { hasSameHydraExternalNodeState, hasSameHydraNodeReferences, isHydraNoOpNodeChange, reconcileHydraExternalNodes, useHydraModelEditorNodeResize } from './useHydraModelEditorNodeResize';

function node(id: string, x = 0, y = 0): Node {
    return { id, position: { x, y }, data: {}, style: { width: 230, height: 92 } };
}

describe('useHydraModelEditorNodeResize', () => {
    it('treats recreated arrays with the same immutable node references as the same external snapshot', () => {
        const first = node('a');
        expect(hasSameHydraNodeReferences([first], [first])).toBe(true);
        expect(hasSameHydraNodeReferences([first], [{ ...first }])).toBe(false);
    });

    it('does not enter an external-sync render loop when the caller recreates the nodes array', () => {
        const committed = vi.fn();
        const first = node('a');
        let renders = 0;
        const { rerender } = renderHook(() => {
            renders += 1;
            return useHydraModelEditorNodeResize([first], committed);
        });
        rerender();
        expect(renders).toBeLessThan(10);
        expect(committed).not.toHaveBeenCalled();
    });


    it('reuses the mounted React Flow node when an external projection is recreated with the same values', () => {
        const local = { ...node('a'), selected: true, measured: { width: 230, height: 92 } };
        const external = { ...node('a'), selected: true, data: {}, style: { width: 230, height: 92 } };
        expect(hasSameHydraExternalNodeState<Node>(external as Node, local as Node)).toBe(true);
        expect(reconcileHydraExternalNodes<Node>([external as Node], [local as Node], new Set())[0]).toBe(local);
    });

    it('treats repeated selection and measurement frames as no-op React Flow changes', () => {
        const current = { ...node('a'), selected: true, measured: { width: 230, height: 92 } };
        expect(isHydraNoOpNodeChange({ id: 'a', type: 'select', selected: true }, current)).toBe(true);
        expect(isHydraNoOpNodeChange({ id: 'a', type: 'dimensions', dimensions: { width: 230, height: 92 }, resizing: false } as NodeChange<Node>, current)).toBe(true);
        expect(isHydraNoOpNodeChange({ id: 'a', type: 'select', selected: false }, current)).toBe(false);
    });

    it('does not replace local node state when the parent rematerializes an already-applied selection', async () => {
        const committed = vi.fn();
        const first = { ...node('a'), selected: false };
        const { result, rerender } = renderHook(({ nodes }) => useHydraModelEditorNodeResize(nodes, committed), { initialProps: { nodes: [first] } });
        act(() => result.current.onNodesChange([{ id: 'a', type: 'select', selected: true }]));
        const selectedLocal = result.current.nodes[0];
        rerender({ nodes: [{ ...node('a'), selected: true }] });
        await waitFor(() => expect(result.current.nodes[0]).toBe(selectedLocal));
        expect(committed).toHaveBeenCalledTimes(1);
    });

    it('does not persist React Flow measurement-only dimension changes', () => {
        const committed = vi.fn();
        const first = node('a');
        const { result } = renderHook(() => useHydraModelEditorNodeResize([first], committed));
        act(() => result.current.onNodesChange([{ id: 'a', type: 'dimensions', dimensions: { width: 230, height: 92 } } as NodeChange<Node>]));
        expect(committed).not.toHaveBeenCalled();
    });

    it('persists only the settled frame of a real resize', () => {
        const committed = vi.fn();
        const first = node('a');
        const { result } = renderHook(() => useHydraModelEditorNodeResize([first], committed));
        act(() => result.current.onNodesChange([{ id: 'a', type: 'dimensions', dimensions: { width: 250, height: 100 }, resizing: true } as NodeChange<Node>]));
        expect(committed).not.toHaveBeenCalled();
        act(() => result.current.onNodesChange([{ id: 'a', type: 'dimensions', dimensions: { width: 260, height: 110 }, resizing: false } as NodeChange<Node>]));
        expect(committed).toHaveBeenCalledTimes(1);
        expect(committed.mock.calls[0]?.[0]?.[0]).toMatchObject({ id: 'a', type: 'dimensions', resizing: false });
    });

    it('materializes newly added external nodes while another node is being dragged', async () => {
        const committed = vi.fn();
        const first = node('a');
        const second = node('b', 300, 0);
        const { result, rerender } = renderHook(({ nodes }) => useHydraModelEditorNodeResize(nodes, committed), { initialProps: { nodes: [first] } });
        act(() => result.current.onNodesChange([{ id: 'a', type: 'position', position: { x: 20, y: 30 }, dragging: true } as NodeChange<Node>]));
        rerender({ nodes: [first, second] });
        await waitFor(() => expect(result.current.nodes.map(item => item.id)).toEqual(['a', 'b']));
        expect(result.current.nodes[0]?.position).toEqual({ x: 20, y: 30 });
    });

    it('reconciles semantic additions without discarding active local geometry', () => {
        const local = [{ ...node('a', 25, 35), measured: { width: 230, height: 92 } }];
        const external = [node('a'), node('b', 300, 40)];
        const next = reconcileHydraExternalNodes(external, local, new Set(['a:position']));
        expect(next.map(item => item.id)).toEqual(['a', 'b']);
        expect(next[0]?.position).toEqual({ x: 25, y: 35 });
        expect(next[1]?.position).toEqual({ x: 300, y: 40 });
    });
});
