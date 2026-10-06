import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

import { MetamodelPropertyPanel } from './MetamodelPropertyPanel';
import { createEmptyMetamodelDiagram } from './diagram';
import { createMetaOperation, createMetaParameter, oneMultiplicity, primitiveType } from './metaModelFactory';
import type { MetaModel } from './types';

vi.mock('../visual-style/NodeStyleEditor', () => ({ NodeStyleEditor: () => null }));
vi.mock('../visual-style/EdgeStyleEditor', () => ({ EdgeStyleEditor: () => null }));
vi.mock('./MetamodelNodeSizeEditor', () => ({ MetamodelNodeSizeEditor: () => null }));
vi.mock('./MetamodelConnectionGeometryEditor', () => ({ MetamodelConnectionGeometryEditor: () => null }));

function model(): MetaModel {
    const parameter = { ...createMetaParameter(), id: 'parameter:Service:find:query', name: 'query' };
    const operation = { ...createMetaOperation(), id: 'operation:Service:find', name: 'find', returnType: primitiveType('STRING'), returnMultiplicity: oneMultiplicity(), behaviorKind: 'QUERY' as const, parameters: [parameter] };
    return {
        schemaVersion: '2.1', id: 'sample', name: 'Sample', annotations: {}, enums: [], dataTypes: [], relations: [],
        classes: [{ id: 'class:Service', name: 'Service', kind: 'INTERFACE', attributes: [], operations: [operation], annotations: {} }]
    };
}

describe('MetamodelPropertyPanel operations', () => {
    it('allows operations to be added to interfaces without disabling their attribute section', () => {
        const onChange = vi.fn();
        const onSelect = vi.fn();
        render(<MetamodelPropertyPanel draft={model()} diagram={createEmptyMetamodelDiagram('sample')} selection={{ kind: 'CLASS', id: 'class:Service' }} onChange={onChange} onDiagramChange={vi.fn()} onSelect={onSelect} onValidationError={vi.fn()} />);

        expect(screen.getByRole('heading', { name: 'Attributes' })).toBeTruthy();
        expect(screen.getByRole('heading', { name: 'Operations' })).toBeTruthy();
        fireEvent.click(screen.getByRole('button', { name: 'Add operation' }));

        expect(onChange).toHaveBeenCalled();
        expect(onSelect).toHaveBeenCalledWith(expect.objectContaining({ kind: 'OPERATION', ownerClassId: 'class:Service' }));
    });

    it('edits operation parameters and exception types', () => {
        const onChange = vi.fn();
        render(<MetamodelPropertyPanel draft={model()} diagram={createEmptyMetamodelDiagram('sample')} selection={{ kind: 'OPERATION', id: 'operation:Service:find', ownerClassId: 'class:Service' }} onChange={onChange} onDiagramChange={vi.fn()} onSelect={vi.fn()} onValidationError={vi.fn()} />);

        expect(screen.getByRole('button', { name: /query : STRING/i })).toBeTruthy();
        fireEvent.click(screen.getByRole('button', { name: 'Add exception' }));
        expect(onChange).toHaveBeenCalled();
    });
});
