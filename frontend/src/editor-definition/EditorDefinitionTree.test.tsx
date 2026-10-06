import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import type { MetaModel } from '../metamodel-designer/types';
import { createEmptyEditorDefinition } from './editorDefinitionFactory';
import { EditorDefinitionTree } from './EditorDefinitionTree';

const metamodel: MetaModel = {
    schemaVersion: '2.1', id: 'organization', name: 'Organization',
    classes: [
        { id: 'class:Department', name: 'Department', kind: 'CLASS', attributes: [{ id: 'attribute:Department:name', name: 'name', type: { kind: 'PRIMITIVE', targetId: 'STRING', externalName: '' }, multiplicity: { lowerBound: 1, upperBound: 1 }, ordered: true, unique: true, defaultValue: '', annotations: {} }], operations: [{ id: 'operation:Department:hire', name: 'hire', returnType: null, returnMultiplicity: null, ordered: true, unique: true, parameters: [{ id: 'parameter:hire:name', name: 'name', type: { kind: 'PRIMITIVE', targetId: 'STRING', externalName: '' }, multiplicity: { lowerBound: 1, upperBound: 1 }, ordered: true, unique: true, annotations: {} }], exceptionTypes: [], behaviorKind: 'COMMAND', annotations: {} }], annotations: { 'hydra:root': 'true' } },
        { id: 'class:Employee', name: 'Employee', kind: 'CLASS', attributes: [], operations: [], annotations: {} }
    ], enums: [], dataTypes: [], relations: [{ id: 'relation:Department:employees', name: 'employees', sourceTypeId: 'class:Department', targetTypeId: 'class:Employee', kind: 'CONTAINMENT', sourceMultiplicity: null, targetMultiplicity: null, oppositeRelationId: '', ordered: true, unique: true, annotations: {} }], annotations: {}
};

function renderTree(onAddClassMapping = vi.fn(), onAddRelationMapping = vi.fn()) {
    const model = createEmptyEditorDefinition(metamodel);
    render(<EditorDefinitionTree model={model} metamodel={metamodel} selection={null} selectedRepresentationId={model.representations[0].id} onAddClassMapping={onAddClassMapping} onAddRelationMapping={onAddRelationMapping} />);
    return { onAddClassMapping, onAddRelationMapping };
}

describe('Sirius-style editor definition tree', () => {
    it('shows the metamodel root and all editor sections', () => {
        renderTree();
        expect(screen.getByText('Organization')).toBeInTheDocument();
        expect(screen.getByText('Visual representations')).toBeInTheDocument();
        expect(screen.getByText('Tool palette')).toBeInTheDocument();
        expect(screen.getByText('Generated forms')).toBeInTheDocument();
    });

    it('shows attributes, operations, parameters and relations under a class', () => {
        renderTree();
        fireEvent.click(screen.getAllByRole('button', { name: /Department.*class:Department/i })[0]);
        fireEvent.click(screen.getByRole('button', { name: /Attributes/i }));
        fireEvent.click(screen.getByRole('button', { name: /Operations/i }));
        fireEvent.click(screen.getByRole('button', { name: /hire/i }));
        fireEvent.click(screen.getByRole('button', { name: /Outgoing relations/i }));
        expect(screen.getAllByText('name')).toHaveLength(2);
        expect(screen.getByText('hire')).toBeInTheDocument();
        expect(screen.getAllByText('employees').length).toBeGreaterThan(0);
    });

    it('offers classes and relations as mappings for the active representation', () => {
        const callbacks = renderTree();
        fireEvent.click(screen.getByRole('button', { name: 'Add Department node mapping' }));
        fireEvent.click(screen.getByRole('button', { name: 'Add employees edge mapping' }));
        expect(callbacks.onAddClassMapping).toHaveBeenCalledWith('class:Department', 'Department');
        expect(callbacks.onAddRelationMapping).toHaveBeenCalledWith('relation:Department:employees');
    });

    it('filters the complete tree without discarding ancestors', () => {
        const model = createEmptyEditorDefinition(metamodel);
        render(<EditorDefinitionTree model={model} metamodel={metamodel} selection={null} selectedRepresentationId={model.representations[0].id} filter="employees" />);
        expect(screen.getByText('Organization')).toBeInTheDocument();
        expect(screen.getAllByText('employees').length).toBeGreaterThan(0);
    });

    it('can be used as a read-only structure tree', () => {
        const model = createEmptyEditorDefinition(metamodel);
        render(<EditorDefinitionTree model={model} metamodel={metamodel} selection={null} selectedRepresentationId={model.representations[0].id} interactive={false} />);
        expect(screen.queryByRole('button', { name: /Add Department node mapping/i })).not.toBeInTheDocument();
    });
    it('automatically expands the branch that contains the selected generated mapping', () => {
        const model = createEmptyEditorDefinition(metamodel);
        const representation = model.representations[0];
        const mapping = {
            id: 'node-mapping:department', targetClassId: 'class:Department', representationName: 'Department', shape: 'RECTANGLE' as const,
            defaultWidth: 220, defaultHeight: 140, minimumWidth: 120, minimumHeight: 80, labelExpressionId: '', imageAssetPath: '', imageFit: 'CONTAIN' as const,
            container: false, containmentRelationIds: [], containerPadding: 18, styles: {}, annotations: {}
        };
        const next = { ...model, representations: [{ ...representation, nodeMappings: [mapping] }] };
        render(<EditorDefinitionTree model={next} metamodel={metamodel} selection={{ kind: 'NODE_MAPPING', id: mapping.id }} selectedRepresentationId={representation.id} />);
        expect(screen.getByText('RECTANGLE')).toBeInTheDocument();
    });

});
