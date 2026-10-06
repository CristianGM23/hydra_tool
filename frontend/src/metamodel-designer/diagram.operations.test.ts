import { describe, expect, it } from 'vitest';

import { buildMetamodelNodes, createEmptyMetamodelDiagram } from './diagram';
import { createMetaOperation, oneMultiplicity, primitiveType } from './metaModelFactory';
import type { MetaModel } from './types';

describe('operation-aware class sizing', () => {
    it('expands old stored nodes for attributes, relation rows and operations', () => {
        const model: MetaModel = {
            schemaVersion: '2.1', id: 'sample', name: 'Sample', annotations: {}, enums: [], dataTypes: [],
            classes: [
                { id: 'class:A', name: 'A', kind: 'CLASS', annotations: {}, attributes: [{ id: 'attribute:A:name', name: 'name', type: primitiveType('STRING'), multiplicity: oneMultiplicity(), ordered: true, unique: true, defaultValue: '', annotations: {} }], operations: [{ ...createMetaOperation(), id: 'operation:A:run', name: 'run' }] },
                { id: 'class:B', name: 'B', kind: 'CLASS', annotations: {}, attributes: [], operations: [] }
            ],
            relations: [{ id: 'relation:A:b', name: 'b', sourceTypeId: 'class:A', targetTypeId: 'class:B', kind: 'REFERENCE', sourceMultiplicity: null, targetMultiplicity: oneMultiplicity(), oppositeRelationId: '', ordered: true, unique: true, annotations: {} }]
        };
        const diagram = { ...createEmptyMetamodelDiagram('sample'), nodes: [{ elementId: 'class:A', x: 0, y: 0, width: 240, height: 110 }] };

        const node = buildMetamodelNodes(model, diagram, []).find(item => item.id === 'class:A');

        expect(Number(node?.height)).toBeGreaterThan(110);
    });
});
