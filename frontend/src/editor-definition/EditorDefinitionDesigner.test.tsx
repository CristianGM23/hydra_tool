import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import type { MetaModel } from '../metamodel-designer/types';
import { EditorDefinitionDesigner } from './EditorDefinitionDesigner';

const metamodel: MetaModel = {
    schemaVersion: '2.1',
    id: 'people',
    name: 'People',
    classes: [
        { id: 'class:Person', name: 'Person', kind: 'CLASS', attributes: [], operations: [], annotations: {} },
        { id: 'class:Department', name: 'Department', kind: 'CLASS', attributes: [], operations: [], annotations: { 'hydra:root': 'true' } },
        { id: 'class:Abstract', name: 'Abstract', kind: 'ABSTRACT_CLASS', attributes: [], operations: [], annotations: {} }
    ],
    enums: [],
    dataTypes: [],
    relations: [{ id: 'relation:Department:people', name: 'people', sourceTypeId: 'class:Department', targetTypeId: 'class:Person', kind: 'CONTAINMENT', sourceMultiplicity: null, targetMultiplicity: null, oppositeRelationId: '', ordered: true, unique: true, annotations: {} }],
    annotations: {}
};

describe('model editor definition designer', () => {
    it('keeps advanced mappings and palette tools behind the explicit detail level', () => {
        render(<EditorDefinitionDesigner metamodel={metamodel} persisted={false} onError={vi.fn()} onMessage={vi.fn()} />);
        expect(screen.getByText(/Switch to All details only when you need mappings, palette tools/i)).toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Add Department node mapping' })).not.toBeInTheDocument();
        fireEvent.click(screen.getByRole('button', { name: 'All details' }));
        expect(screen.getByRole('button', { name: 'Add Department node mapping' })).toBeInTheDocument();
    });

    it('creates several representations inside one model editor', () => {
        render(<EditorDefinitionDesigner metamodel={metamodel} persisted={false} onError={vi.fn()} onMessage={vi.fn()} />);
        fireEvent.click(screen.getByRole('button', { name: '+ Representation' }));
        expect(screen.getByLabelText('Name')).toHaveValue('Main diagram');
        expect(screen.getByLabelText('Representation ID')).toBeInTheDocument();
    });

    it('creates a separate editor definition for the same metamodel', async () => {
        render(<EditorDefinitionDesigner metamodel={metamodel} persisted={false} onError={vi.fn()} onMessage={vi.fn()} />);
        const firstId = (screen.getByLabelText('Model editor') as HTMLSelectElement).value;
        fireEvent.click(screen.getByRole('button', { name: '+ New editor' }));
        await waitFor(() => expect(screen.getByLabelText('Model editor')).not.toHaveValue(firstId), { timeout:10_000 });
        const settings = screen.getByText('Editor-wide settings').closest('section')!;
        if (within(settings).queryByRole('button', { name: 'Show' }) !== null) fireEvent.click(within(settings).getByRole('button', { name: 'Show' }));
        fireEvent.click(screen.getByRole('button', { name: 'Advanced options' }));
        expect(screen.getByLabelText('Editor ID')).toHaveValue((screen.getByLabelText('Model editor') as HTMLSelectElement).value);
    });

    it('adds only concrete metamodel classes to the editor palette while abstract classes remain available for appearance', () => {
        render(<EditorDefinitionDesigner metamodel={metamodel} persisted={false} onError={vi.fn()} onMessage={vi.fn()} />);
        fireEvent.click(screen.getByRole('button', { name: 'All details' }));
        expect(screen.getByRole('button', { name: 'Add Person node mapping' })).toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Add Abstract node mapping' })).not.toBeInTheDocument();
        fireEvent.click(screen.getByRole('button', { name: 'Element representations' }));
        fireEvent.click(screen.getByRole('button', { name: /Abstract.*0\/1/i }));
        expect(screen.getByText(/no creation tool/i)).toBeInTheDocument();
    });

    it('opens editor and representation process guides from the left panel', () => {
        render(<EditorDefinitionDesigner metamodel={metamodel} persisted={false} onError={vi.fn()} onMessage={vi.fn()} />);
        fireEvent.click(screen.getByRole('button', { name: 'Editor guide' }));
        expect(screen.getByRole('dialog')).toHaveTextContent('How to define a model editor');
        fireEvent.click(screen.getByRole('button', { name: 'Close guide' }));
        fireEvent.click(screen.getByRole('button', { name: 'Representation guide' }));
        expect(screen.getByRole('dialog')).toHaveTextContent('How to define a representation');
    });

    it('provides contextual help for representation identifiers', () => {
        render(<EditorDefinitionDesigner metamodel={metamodel} persisted={false} onError={vi.fn()} onMessage={vi.fn()} />);
        fireEvent.click(screen.getByRole('button', { name: 'Information about Representation ID' }));
        expect(screen.getByRole('tooltip')).toHaveTextContent(/mappings, palette tools and persisted diagrams/i);
    });

    it('exposes a separate runtime preview without replacing the editor definition form', () => {
        render(<EditorDefinitionDesigner metamodel={metamodel} persisted={false} onError={vi.fn()} onMessage={vi.fn()} />);
        expect(screen.getByRole('button', { name: 'Definition' })).toHaveClass('active');
        fireEvent.click(screen.getByRole('button', { name: 'Runtime preview' }));
        expect(screen.getByText('Compiled editor runtime')).toBeInTheDocument();
        expect(screen.getByText(/No executable plan compiled/i)).toBeInTheDocument();
    });

    it('automatically proposes the semantic root while allowing manual changes', () => {
        render(<EditorDefinitionDesigner metamodel={metamodel} persisted={false} onError={vi.fn()} onMessage={vi.fn()} />);
        expect(screen.getByText(/Department.*marked explicitly as a root/i)).toBeInTheDocument();
        expect(screen.getByRole('checkbox', { name: /Department.*automatic/i })).toBeChecked();
    });

    it('opens, collapses and closes the generated structure tree tab', () => {
        render(<EditorDefinitionDesigner metamodel={metamodel} persisted={false} onError={vi.fn()} onMessage={vi.fn()} />);
        fireEvent.click(screen.getByRole('button', { name: 'Structure tree' }));
        expect(screen.getByText('Generated editor structure')).toBeInTheDocument();
        fireEvent.click(screen.getByRole('button', { name: 'Collapse tree' }));
        expect(screen.getByRole('button', { name: 'Expand tree' })).toBeInTheDocument();
        fireEvent.click(screen.getByRole('button', { name: 'Close structure tree tab' }));
        expect(screen.getByRole('button', { name: '+ Structure tree' })).toBeInTheDocument();
    });

    it('configures a mapped class as a visual container', () => {
        render(<EditorDefinitionDesigner metamodel={metamodel} persisted={false} onError={vi.fn()} onMessage={vi.fn()} />);
        fireEvent.click(screen.getByRole('button', { name: 'All details' }));
        fireEvent.click(screen.getByRole('button', { name: 'Add Department node mapping' }));
        fireEvent.click(screen.getByLabelText('Can contain other elements'));
        expect(screen.getByText('Accepted containment relations')).toBeInTheDocument();
        expect(screen.getByLabelText(/people.*Person/i)).toBeChecked();
        expect(screen.getByLabelText('Container padding')).toHaveValue(18);
    });

    it('offers project SVG and PNG representations', () => {
        render(<EditorDefinitionDesigner metamodel={metamodel} persisted={false} onError={vi.fn()} onMessage={vi.fn()} />);
        fireEvent.click(screen.getByRole('button', { name: 'All details' }));
        fireEvent.click(screen.getByRole('button', { name: 'Add Department node mapping' }));
        const projectImage = screen.getByRole('option', { name: 'Project image' });
        expect(projectImage).toBeInTheDocument();
        expect(projectImage).toHaveAttribute('title', expect.stringMatching(/SVG, PNG, JPEG, GIF or WebP/i));
    });

    it('opens editable mapping properties from the generated structure tree', async () => {
        render(<EditorDefinitionDesigner metamodel={metamodel} persisted={false} onError={vi.fn()} onMessage={vi.fn()} />);
        fireEvent.click(screen.getByRole('button', { name: 'All details' }));
        fireEvent.click(screen.getByRole('button', { name: 'Add Department node mapping' }));
        fireEvent.click(screen.getByRole('button', { name: 'Structure tree' }));
        const tree = screen.getByLabelText('Editor definition tree');
        fireEvent.click(within(tree).getByText('RECTANGLE').closest('button')!);
        expect(await screen.findByLabelText('Visible name')).toBeInTheDocument();
        fireEvent.change(screen.getByLabelText('Visible name'), { target: { value: 'Department container' } });
        await waitFor(() => expect(within(tree).getByText('Department container')).toBeInTheDocument(), { timeout:10_000 });
    });

});
