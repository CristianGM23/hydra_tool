import type { Node } from '@xyflow/react';
import { describe, expect, it } from 'vitest';

import { clearMetamodelNodeSelection } from './canvasNodeSelection';

describe('clearMetamodelNodeSelection', () => {
    it('clears every selected class, enumeration and data type', () => {
        const nodes: Node[] = [
            { id: 'class', position: { x: 0, y: 0 }, data: {}, selected: true },
            { id: 'enum', position: { x: 10, y: 0 }, data: {}, selected: true },
            { id: 'datatype', position: { x: 20, y: 0 }, data: {}, selected: true }
        ];

        expect(clearMetamodelNodeSelection(nodes).every(node => node.selected === false)).toBe(true);
    });

    it('does not mutate the node objects that were selected', () => {
        const selected: Node = { id: 'class', position: { x: 0, y: 0 }, data: {}, selected: true };
        const result = clearMetamodelNodeSelection([selected]);

        expect(selected.selected).toBe(true);
        expect(result[0]).not.toBe(selected);
    });

    it('preserves the same collection when there is no selected figure', () => {
        const nodes: Node[] = [{ id: 'class', position: { x: 0, y: 0 }, data: {}, selected: false }];
        expect(clearMetamodelNodeSelection(nodes)).toBe(nodes);
    });
});
