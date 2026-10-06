import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import type { MetaModel } from '../metamodel-designer/types';
import { createEmptyEditorDefinition } from './editorDefinitionFactory';
import { EditorDefinitionTree } from './EditorDefinitionTree';
import { EditorDocumentForm, FormEditor, RepresentationForm } from './EditorDefinitionDesignerParts';
import { detectMetamodelRoots } from './metamodelRootDetection';
import type { FormDefinition } from './types';

const metamodel: MetaModel = {
    schemaVersion: '2.1', id: 'project', name: 'Project',
    classes: [
        { id: 'class:Project', name: 'Project', kind: 'CLASS', attributes: [{ id: 'attribute:Project:name', name: 'name', type: { kind: 'PRIMITIVE', targetId: 'STRING', externalName: '' }, multiplicity: { lowerBound: 1, upperBound: 1 }, ordered: true, unique: true, defaultValue: '', annotations: {} }], operations: [], annotations: { 'hydra:root': 'true' } },
        { id: 'class:Task', name: 'Task', kind: 'CLASS', attributes: [], operations: [], annotations: {} }
    ],
    enums: [], dataTypes: [],
    relations: [{ id: 'relation:Project:tasks', name: 'tasks', sourceTypeId: 'class:Project', targetTypeId: 'class:Task', kind: 'CONTAINMENT', sourceMultiplicity: null, targetMultiplicity: null, oppositeRelationId: '', ordered: true, unique: true, annotations: {} }],
    annotations: {}
};

const capabilities = ['MODEL_EDITING', 'MULTIPLE_REPRESENTATIONS', 'OPERATIONS', 'VALIDATION_RUNTIME', 'QUICK_FIX_RUNTIME', 'EVENTS', 'EVENT_FLOWS', 'GAMIFICATION', 'IMPORT', 'EXPORT', 'UNDO_REDO'] as const;

describe('V4.30.0d progressive model editor authoring', () => {
    it('keeps the structure tree high-level until all details are requested', () => {
        const model = createEmptyEditorDefinition(metamodel);
        const { rerender } = render(<EditorDefinitionTree model={model} metamodel={metamodel} selection={null} selectedRepresentationId={model.representations[0].id} detailLevel="ESSENTIAL" />);
        expect(screen.getByText('Visual representations')).not.toBeNull();
        expect(screen.queryByText('Enumerations')).toBeNull();
        expect(screen.queryByRole('button', { name: /Add Project node mapping/i })).toBeNull();
        rerender(<EditorDefinitionTree model={model} metamodel={metamodel} selection={null} selectedRepresentationId={model.representations[0].id} detailLevel="FULL" />);
        expect(screen.getByText('Enumerations')).not.toBeNull();
        expect(screen.getByRole('button', { name: /Add Project node mapping/i })).not.toBeNull();
    });

    it('shows representation controls only when the chosen view type needs them', () => {
        const model = createEmptyEditorDefinition(metamodel);
        const base = model.representations[0];
        const roots = detectMetamodelRoots(metamodel);
        const props = { metamodel, model, detectedRoots: roots, onChange: vi.fn(), onRenameId: vi.fn(), onAddClassTool: vi.fn(), onAddRelationTool: vi.fn(), onEditNodeMapping: vi.fn(), onEditEdgeMapping: vi.fn(), onDelete: vi.fn() };
        const { rerender } = render(<RepresentationForm {...props} representation={{ ...base, kind: 'FORM' }} />);
        expect(screen.getByText('No node mappings required')).not.toBeNull();
        expect(screen.queryByText('Relationships shown in this view')).toBeNull();
        rerender(<RepresentationForm {...props} representation={{ ...base, kind: 'DIAGRAM' }} />);
        expect(screen.getByText('Elements shown in this view')).not.toBeNull();
        expect(screen.getByText('Relationships shown in this view')).not.toBeNull();
    });

    it('hides validation and event settings when the selected editor profile does not support them', () => {
        const model = createEmptyEditorDefinition(metamodel);
        const standard = { ...model, capabilityManifest: { capabilities: ['MODEL_EDITING', 'IMPORT', 'EXPORT', 'UNDO_REDO'] as typeof model.capabilityManifest.capabilities } };
        const { rerender } = render(<EditorDocumentForm model={standard} etag="" capabilities={[...capabilities]} onChange={vi.fn()} />);
        expect(screen.queryByText('Validation')).toBeNull();
        expect(screen.queryByText('Events and reactions')).toBeNull();
        const full = { ...model, capabilityManifest: { capabilities: [...capabilities] } };
        rerender(<EditorDocumentForm model={full} etag="" capabilities={[...capabilities]} onChange={vi.fn()} />);
        expect(screen.getByText('Validation')).not.toBeNull();
        expect(screen.getByText('Events and reactions')).not.toBeNull();
    });

    it('removes edit-only field choices for read-only form widgets', () => {
        const form: FormDefinition = {
            id: 'form:project', name: 'Project properties', targetClassId: 'class:Project', sections: [{ id: 'section:general', title: 'General', order: 0, columns: 1, fields: [{ id: 'field:name', featureId: 'attribute:Project:name', label: 'Name', widget: 'READ_ONLY', required: false, readOnly: true, visibleExpressionId: '', enabledExpressionId: '', helpText: '' }] }]
        };
        render(<FormEditor form={form} metamodel={metamodel} onChange={vi.fn()} onRenameId={vi.fn()} onDelete={vi.fn()} />);
        expect(screen.getByText('READ_ONLY')).not.toBeNull();
        expect(screen.queryByText('Required')).toBeNull();
        expect(screen.queryByText('Read only')).toBeNull();
    });
});
