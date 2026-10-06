import { describe, expect, it } from 'vitest';
import type { Edge, Node } from '@xyflow/react';

import { buildDiagramDocument } from './diagramUtils';

describe('diagramUtils', () => {
    it('preserves node position dimensions and representation', () => {
        const nodes: Node[] = [{ id: 'element-1', position: { x: 40, y: 60 }, measured: { width: 220, height: 140 }, data: { notation: { id: 'node-card' } } }];

        const result = buildDiagramDocument('model-1.xmi', 'default-notation', nodes, []);

        expect(result.nodes[0]).toEqual({ elementId: 'element-1', nodeStyleId: 'node-card', x: 40, y: 60, width: 220, height: 140 });
    });

    it('uses style dimensions when the node has not been measured', () => {
        const nodes: Node[] = [{ id: 'element-1', position: { x: 0, y: 0 }, style: { width: 180, height: 90 }, data: {} }];

        const result = buildDiagramDocument('model-1.xmi', 'default-notation', nodes, []);

        expect(result.nodes[0].width).toBe(180);
        expect(result.nodes[0].height).toBe(90);
    });

    it('preserves semantic reference and edge style', () => {
        const edges: Edge[] = [{ id: 'edge-1', source: 'element-1', target: 'element-2', data: { label: 'target', notation: { id: 'node-target' } } }];

        const result = buildDiagramDocument('model-1.xmi', 'default-notation', [], edges);

        expect(result.edges[0]).toEqual({ sourceId: 'element-1', referenceName: 'target', targetId: 'element-2', edgeStyleId: 'node-target' });
    });
});