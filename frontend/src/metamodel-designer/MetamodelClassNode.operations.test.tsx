import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

import { DEFAULT_CLASS_NODE_STYLE } from '../visual-style/visualStyleUtils';
import { MetamodelClassNode } from './MetamodelClassNode';
import type { MetamodelClassNodeData } from './diagram';
import type { MetaClass } from './types';

vi.mock('@xyflow/react', () => ({ NodeResizer: () => null }));
vi.mock('./MetamodelHandles', () => ({ MetamodelHandles: () => null }));

describe('MetamodelClassNode operations', () => {
    it('keeps attributes derived from relations visible next to the independent operations compartment', () => {
        const classDesign: MetaClass = {
            id: 'class:Customer', name: 'Customer', kind: 'CLASS', annotations: {},
            attributes: [{ id: 'attribute:Customer:name', name: 'name', type: { kind: 'PRIMITIVE', targetId: 'STRING', externalName: '' }, multiplicity: { lowerBound: 1, upperBound: 1 }, ordered: true, unique: true, defaultValue: '', annotations: {} }],
            operations: [{ id: 'operation:Customer:displayName', name: 'displayName', returnType: { kind: 'PRIMITIVE', targetId: 'STRING', externalName: '' }, returnMultiplicity: { lowerBound: 1, upperBound: 1 }, ordered: true, unique: true, parameters: [], exceptionTypes: [], behaviorKind: 'QUERY', annotations: {} }]
        };
        const data: MetamodelClassNodeData = {
            classDesign,
            relations: [{ id: 'relation:Customer:orders', name: 'orders', sourceTypeId: 'class:Customer', targetTypeId: 'class:Order', kind: 'REFERENCE', sourceMultiplicity: null, targetMultiplicity: { lowerBound: 0, upperBound: null }, oppositeRelationId: '', ordered: true, unique: true, annotations: {} }],
            classNames: { 'class:Customer': 'Customer', 'class:Order': 'Order' },
            typeNames: { 'primitive:STRING': 'String' }, visualStyle: DEFAULT_CLASS_NODE_STYLE, ports: []
        };

        const props = { data, selected: false, isConnectable: false } as unknown as Parameters<typeof MetamodelClassNode>[0];
        render(<MetamodelClassNode {...props} />);

        expect(screen.getByText(/name : String/)).toBeTruthy();
        expect(screen.getByText(/orders : Order/)).toBeTruthy();
        expect(screen.getByText(/displayName\(\) : String/)).toBeTruthy();
    });
});
