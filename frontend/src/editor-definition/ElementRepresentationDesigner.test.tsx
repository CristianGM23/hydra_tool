import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import type { MetaModel } from '../metamodel-designer/types';
import { EditorDefinitionDesigner } from './EditorDefinitionDesigner';

const metamodel: MetaModel = {
    schemaVersion: '2.1', id: 'people', name: 'People',
    classes: [
        { id: 'class:Person', name: 'Person', kind: 'CLASS', attributes: [], operations: [], annotations: {} },
        { id: 'class:Department', name: 'Department', kind: 'CLASS', attributes: [], operations: [], annotations: { 'hydra:root': 'true' } },
        { id: 'class:Party', name: 'Party', kind: 'ABSTRACT_CLASS', attributes: [], operations: [], annotations: {} }
    ],
    enums: [], dataTypes: [],
    relations: [{ id: 'relation:Department:people', name: 'people', sourceTypeId: 'class:Department', targetTypeId: 'class:Person', kind: 'CONTAINMENT', sourceMultiplicity: null, targetMultiplicity: null, oppositeRelationId: '', ordered: true, unique: true, annotations: {} }], annotations: {}
};

describe('V4.32.39 metamodel element representations', () => {
    it('defines and edits a class appearance from the high-level element view', () => {
        render(<EditorDefinitionDesigner metamodel={metamodel} persisted={false} onError={vi.fn()} onMessage={vi.fn()} />);
        fireEvent.click(screen.getByRole('button', { name: 'Element representations' }));
        fireEvent.click(screen.getByRole('button', { name: /Department.*0\/1/i }));
        expect(screen.getByText(/Choose the views where this element appears/i)).toBeInTheDocument();
        fireEvent.click(screen.getByRole('button', { name: /Show in this view/i }));
        expect(screen.getByText('Shown in this view')).toBeInTheDocument();
        expect(screen.getByText('Rectangle')).toBeInTheDocument();
        fireEvent.click(screen.getByRole('button', { name: 'Edit element appearance' }));
        expect(screen.getByLabelText('Shape')).toBeInTheDocument();
        expect(screen.getByRole('option', { name: 'Project image' })).toBeInTheDocument();
    });

    it('defines and edits relationship appearance without leaving the high-level workflow first', () => {
        render(<EditorDefinitionDesigner metamodel={metamodel} persisted={false} onError={vi.fn()} onMessage={vi.fn()} />);
        fireEvent.click(screen.getByRole('button', { name: 'Element representations' }));
        fireEvent.click(screen.getByRole('button', { name: /people.*Department.*Person/i }));
        fireEvent.click(screen.getByRole('button', { name: /Show in this view/i }));
        expect(screen.getByText('Orthogonal route')).toBeInTheDocument();
        fireEvent.click(screen.getByRole('button', { name: 'Edit relationship appearance' }));
        expect(screen.getByLabelText('Line routing')).toBeInTheDocument();
    });
});
