import { describe, expect, it } from 'vitest';
import type { Node } from '@xyflow/react';
import { arrangeDiagramSelection, positionChangesForSelection } from './diagramSelection';

function node(id: string, x: number, y: number): Node<Record<string, unknown>> {
    return { id, position:{ x, y }, data:{}, width:100, height:60 };
}

describe('shared diagram selection layout', () => {
    it('aligns only the selected nodes on one vertical line', () => {
        const nodes=[node('a',120,20),node('b',300,160),node('c',600,80)];
        const result=arrangeDiagramSelection(nodes,['a','b'],'ALIGN_LEFT',20);
        expect(result[0].position.x).toBe(120);
        expect(result[1].position.x).toBe(120);
        expect(result[2].position).toEqual(nodes[2].position);
    });

    it('distributes three selected nodes and emits standard React Flow position changes', () => {
        const nodes=[node('a',0,0),node('b',250,100),node('c',600,200)];
        const result=arrangeDiagramSelection(nodes,['a','b','c'],'DISTRIBUTE_HORIZONTAL',40);
        const changes=positionChangesForSelection(nodes,result,['a','b','c']);
        expect(result[1].position.x).toBeGreaterThan(result[0].position.x);
        expect(result[2].position.x).toBeGreaterThan(result[1].position.x);
        expect(changes.some(change => 'id' in change && change.id === 'b')).toBe(true);
    });
});
