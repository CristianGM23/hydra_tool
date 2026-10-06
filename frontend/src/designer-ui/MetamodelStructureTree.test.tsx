import { fireEvent, render, screen, within } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import type { MetaModel } from '../metamodel-designer/types';
import { buildMetamodelStructure } from './metamodelStructureModel';
import { MetamodelSelectionProperties, MetamodelStructureTree } from './MetamodelStructureTree';

const metamodel: MetaModel = {
    schemaVersion: '2.1', id: 'shop', name: 'Shop', annotations: {},
    classes: [
        { id: 'catalog', name: 'Catalog', kind: 'CLASS', annotations: {}, attributes: [{ id: 'title', name: 'title', type: { kind: 'PRIMITIVE', targetId: 'STRING', externalName: '' }, multiplicity: { lowerBound: 1, upperBound: 1 }, ordered: false, unique: true, defaultValue: '', annotations: {} }], operations: [{ id: 'publish', name: 'publish', returnType: null, returnMultiplicity: null, ordered: false, unique: true, parameters: [{ id: 'channel', name: 'channel', type: { kind: 'PRIMITIVE', targetId: 'STRING', externalName: '' }, multiplicity: { lowerBound: 1, upperBound: 1 }, ordered: false, unique: true, annotations: {} }], exceptionTypes: [], behaviorKind: 'COMMAND', annotations: {} }] },
        { id: 'product', name: 'Product', kind: 'CLASS', annotations: {}, attributes: [], operations: [] }
    ],
    enums: [{ id: 'status', name: 'Status', annotations: {}, literals: [{ id: 'active', name: 'Active', value: 1, annotations: {} }] }],
    dataTypes: [{ id: 'money', name: 'Money', annotations: {} }],
    relations: [{ id: 'products', name: 'products', sourceTypeId: 'catalog', targetTypeId: 'product', kind: 'CONTAINMENT', sourceMultiplicity: null, targetMultiplicity: { lowerBound: 0, upperBound: null }, oppositeRelationId: '', ordered: true, unique: true, annotations: {} }]
};

describe('MetamodelStructureTree', () => {
    it('builds a semantic model root', () => {
        const nodes = buildMetamodelStructure(metamodel);
        expect(nodes).toHaveLength(1);
        expect(nodes[0]).toMatchObject({ label: 'Shop', badge: 'METAMODEL' });
    });

    it('shows classes, attributes, operations and relations', () => {
        render(<MetamodelStructureTree metamodel={metamodel} filter="" selection={null} onSelect={vi.fn()} />);
        const tree = screen.getByLabelText('Semantic metamodel structure tree');
        expect(within(tree).getByText('Catalog')).toBeInTheDocument();
        fireEvent.click(within(tree).getByText('Catalog').closest('button')!);
        expect(within(tree).getByText('Attributes')).toBeInTheDocument();
        expect(within(tree).getByText('Operations')).toBeInTheDocument();
        expect(within(tree).getAllByText('products').length).toBeGreaterThan(0);
    });

    it('opens operation parameters under their owner operation', () => {
        render(<MetamodelStructureTree metamodel={metamodel} filter="publish" selection={null} onSelect={vi.fn()} />);
        expect(screen.getByText('publish')).toBeInTheDocument();
        expect(screen.getByText('channel')).toBeInTheDocument();
    });

    it('returns the selected semantic element without editing it', () => {
        const onSelect = vi.fn();
        render(<MetamodelStructureTree metamodel={metamodel} filter="Product" selection={null} onSelect={onSelect} />);
        fireEvent.click(screen.getByText('Product').closest('button')!);
        expect(onSelect).toHaveBeenCalledWith(expect.objectContaining({ kind: 'CLASS', id: 'product', label: 'Product' }));
    });

    it('renders a safe unavailable state when the metamodel cannot be loaded', () => {
        render(<MetamodelStructureTree metamodel={null} filter="" selection={null} onSelect={vi.fn()} />);
        expect(screen.getByLabelText('Metamodel structure unavailable')).toHaveTextContent('Metamodel unavailable');
    });

    it('shows read-only semantic properties', () => {
        render(<MetamodelSelectionProperties metamodel={metamodel} selection={{ kind: 'CLASS', id: 'catalog', label: 'Catalog', badge: 'Class' }} />);
        expect(screen.getByText('Connected relations')).toBeInTheDocument();
        expect(screen.getByText(/Edit it in Metamodel designer/)).toBeInTheDocument();
    });
});
