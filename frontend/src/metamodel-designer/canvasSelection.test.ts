import { describe, expect, it } from 'vitest';
import type { Edge, Node } from '@xyflow/react';

import { arrangeSelectedNodes, findInternalRelationIds, findNodesInsideSelection, isMeaningfulSelectionRectangle, normalizeSelectionRectangle } from './canvasSelection';

const nodes: Node[] = [
    { id: 'root', position: { x: 20, y: 20 }, width: 100, height: 80, data: {} },
    { id: 'child', position: { x: 180, y: 40 }, width: 120, height: 90, data: {} },
    { id: 'grandchild', position: { x: 360, y: 200 }, width: 100, height: 80, data: {} }
];

const edges: Edge[] = [
    { id: 'root-child', source: 'root', target: 'child' },
    { id: 'child-grandchild', source: 'child', target: 'grandchild' }
];

describe('canvas area selection helpers', () => {
    it('normalizes a rectangle dragged in any direction', () => {
        expect(normalizeSelectionRectangle(300, 200, 50, 40)).toEqual({ left: 50, top: 40, right: 300, bottom: 200, width: 250, height: 160 });
    });

    it('selects every partially intersected node and internal relation', () => {
        expect(findNodesInsideSelection(nodes, normalizeSelectionRectangle(0, 0, 320, 150))).toEqual(['root', 'child']);
        expect(findInternalRelationIds(edges, ['root', 'child'])).toEqual(['root-child']);
    });

    it('places aligned nodes on one horizontal line with a margin', () => {
        const result = arrangeSelectedNodes(nodes, ['root', 'child', 'grandchild'], 'ALIGN_TOP', [], { nodeGap: 40 });
        const selected = result.filter(node => ['root', 'child', 'grandchild'].includes(node.id));

        expect(new Set(selected.map(node => node.position.y)).size).toBe(1);
        expect(selected[1].position.x - selected[0].position.x).toBeGreaterThanOrEqual(140);
        expect(selected[2].position.x - selected[1].position.x).toBeGreaterThanOrEqual(160);
    });

    it('places aligned nodes on one vertical line with a margin', () => {
        const result = arrangeSelectedNodes(nodes, ['root', 'child', 'grandchild'], 'ALIGN_LEFT', [], { nodeGap: 30 });
        const selected = result.filter(node => ['root', 'child', 'grandchild'].includes(node.id));

        expect(new Set(selected.map(node => node.position.x)).size).toBe(1);
        expect(selected[1].position.y - selected[0].position.y).toBeGreaterThanOrEqual(110);
    });

    it('builds containment levels from root to grandchild', () => {
        const result = arrangeSelectedNodes(nodes, ['root', 'child', 'grandchild'], 'TREE', [{ sourceId: 'root', targetId: 'child' }, { sourceId: 'child', targetId: 'grandchild' }], { nodeGap: 50, treeLevelGap: 80 });
        const root = result.find(node => node.id === 'root')!;
        const child = result.find(node => node.id === 'child')!;
        const grandchild = result.find(node => node.id === 'grandchild')!;

        expect(root.position.y).toBeLessThan(child.position.y);
        expect(child.position.y).toBeLessThan(grandchild.position.y);
    });

    it('uses the first selected node as root when there is no containment', () => {
        const result = arrangeSelectedNodes(nodes, ['root', 'child', 'grandchild'], 'TREE');
        const root = result.find(node => node.id === 'root')!;
        const child = result.find(node => node.id === 'child')!;

        expect(root.position.y).toBeLessThan(child.position.y);
    });

    it('rejects a tiny pointer movement as a selection area', () => {
        expect(isMeaningfulSelectionRectangle(normalizeSelectionRectangle(10, 10, 12, 13))).toBe(false);
    });
});

describe('hierarchical tree arrangement', () => {
    const hierarchyNodes: Node[] = [
        { id: 'root', position: { x: 400, y: 300 }, width: 120, height: 70, data: {} },
        { id: 'parent-a', position: { x: 50, y: 60 }, width: 130, height: 80, data: {} },
        { id: 'parent-b', position: { x: 800, y: 90 }, width: 110, height: 75, data: {} },
        { id: 'a-1', position: { x: 20, y: 500 }, width: 100, height: 60, data: {} },
        { id: 'a-2', position: { x: 180, y: 520 }, width: 140, height: 90, data: {} },
        { id: 'b-1', position: { x: 700, y: 510 }, width: 105, height: 65, data: {} }
    ];
    const hierarchy = [
        { sourceId: 'root', targetId: 'parent-a' },
        { sourceId: 'root', targetId: 'parent-b' },
        { sourceId: 'parent-a', targetId: 'a-1' },
        { sourceId: 'parent-a', targetId: 'a-2' },
        { sourceId: 'parent-b', targetId: 'b-1' }
    ];

    function arrange(relations = hierarchy, selectedIds = hierarchyNodes.map(node => node.id)): Node[] {
        return arrangeSelectedNodes(hierarchyNodes, selectedIds, 'TREE', relations, { nodeGap: 40, treeLevelGap: 70 });
    }

    it('places a containing class above its child parent classes', () => {
        const result = arrange();
        expect(result.find(node => node.id === 'root')!.position.y).toBeLessThan(result.find(node => node.id === 'parent-a')!.position.y);
    });

    it('places an intermediate parent below its own container', () => {
        const result = arrange();
        expect(result.find(node => node.id === 'parent-a')!.position.y).toBeGreaterThan(result.find(node => node.id === 'root')!.position.y);
    });

    it('places children below their direct parent', () => {
        const result = arrange();
        expect(result.find(node => node.id === 'a-1')!.position.y).toBeGreaterThan(result.find(node => node.id === 'parent-a')!.position.y);
        expect(result.find(node => node.id === 'a-2')!.position.y).toBeGreaterThan(result.find(node => node.id === 'parent-a')!.position.y);
    });

    it('places sibling parent classes at exactly the same tree height', () => {
        const result = arrange();
        expect(result.find(node => node.id === 'parent-a')!.position.y).toBe(result.find(node => node.id === 'parent-b')!.position.y);
    });

    it('places sibling leaf classes at exactly the same tree height', () => {
        const result = arrange();
        expect(result.find(node => node.id === 'a-1')!.position.y).toBe(result.find(node => node.id === 'a-2')!.position.y);
    });

    it('centres a parent over the horizontal span of its children', () => {
        const result = arrange();
        const parent = result.find(node => node.id === 'parent-a')!;
        const first = result.find(node => node.id === 'a-1')!;
        const second = result.find(node => node.id === 'a-2')!;
        const parentCentre = parent.position.x + 65;
        const childrenCentre = (first.position.x + (second.position.x + 140)) / 2;
        expect(Math.abs(parentCentre - childrenCentre)).toBeLessThanOrEqual(1);
    });

    it('keeps sibling subtrees contiguous and non-overlapping', () => {
        const result = arrange();
        const a2 = result.find(node => node.id === 'a-2')!;
        const b1 = result.find(node => node.id === 'b-1')!;
        expect(a2.position.x + 140 + 40).toBeLessThanOrEqual(b1.position.x);
    });

    it('keeps independent selected roots at the same height when containment exists', () => {
        const selectedIds = ['parent-a', 'parent-b', 'a-1', 'a-2', 'b-1'];
        const relations = hierarchy.filter(relation => relation.sourceId !== 'root');
        const result = arrange(relations, selectedIds);
        expect(result.find(node => node.id === 'parent-a')!.position.y).toBe(result.find(node => node.id === 'parent-b')!.position.y);
    });

    it('ignores duplicate containment edges deterministically', () => {
        const result = arrange([...hierarchy, hierarchy[0], hierarchy[2]]);
        const baseline = arrange();
        expect(result.map(node => node.position)).toEqual(baseline.map(node => node.position));
    });

    it('does not recurse forever when malformed containment contains a cycle', () => {
        const result = arrange([{ sourceId: 'parent-a', targetId: 'parent-b' }, { sourceId: 'parent-b', targetId: 'parent-a' }], ['parent-a', 'parent-b']);
        expect(result.find(node => node.id === 'parent-a')!.position.y).not.toBeNaN();
        expect(result.find(node => node.id === 'parent-b')!.position.y).not.toBeNaN();
    });

    it('uses level maximum height to prevent vertical overlap', () => {
        const result = arrange();
        const parentLevelBottom = Math.max(
            result.find(node => node.id === 'parent-a')!.position.y + 80,
            result.find(node => node.id === 'parent-b')!.position.y + 75
        );
        const childTop = result.find(node => node.id === 'a-1')!.position.y;
        expect(childTop - parentLevelBottom).toBeGreaterThanOrEqual(70);
    });

    it('preserves the historical first-root fallback when there is no containment', () => {
        const result = arrange([], ['root', 'parent-a', 'parent-b']);
        const root = result.find(node => node.id === 'root')!;
        const parentA = result.find(node => node.id === 'parent-a')!;
        const parentB = result.find(node => node.id === 'parent-b')!;
        expect(root.position.y).toBeLessThan(parentA.position.y);
        expect(parentA.position.y).toBe(parentB.position.y);
    });
});
