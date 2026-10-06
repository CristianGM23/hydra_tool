import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import type { MetaModel } from '../metamodel-designer/types';
import { EditorRuntimePreview } from './EditorRuntimePreview';
import type { EditorDefinitionCompilationResult, ExecutableRepresentationPlan } from './types';

const metamodel: MetaModel = {
    schemaVersion: '2.1', id: 'people', name: 'People', enums: [], dataTypes: [], annotations: {},
    classes: [{ id: 'class:Person', name: 'Person', kind: 'CLASS', attributes: [], operations: [], annotations: {} }],
    relations: [{ id: 'relation:knows', name: 'knows', kind: 'REFERENCE', sourceTypeId: 'class:Person', targetTypeId: 'class:Person', sourceMultiplicity: null, targetMultiplicity: { lowerBound: 0, upperBound: -1 }, oppositeRelationId: '', ordered: false, unique: true, annotations: {} }]
};

function representation(id: string, name: string): ExecutableRepresentationPlan {
    return {
        id, name, kind: 'DIAGRAM', rootClassIds: ['class:Person'], editable: true,
        nodeMappingsByClassId: { 'class:Person': [{ id: `node:${id}`, targetClassId: 'class:Person', representationName: 'Person', shape: 'ROUNDED_RECTANGLE', defaultWidth: 180, defaultHeight: 90, minimumWidth: 120, minimumHeight: 70, labelExpressionId: '', styles: {}, annotations: {} }] },
        edgeMappingsByRelationId: {}, toolsById: {}, paletteGroups: [], annotations: {}
    };
}

const result: EditorDefinitionCompilationResult = {
    cacheHit: false,
    checkReport: { editorDefinitionId: 'people-editor', diagnostics: [], valid: true },
    plan: {
        schemaVersion: '1.0', id: 'people-editor:compiled', sourceEditorDefinitionId: 'people-editor', sourceEtag: 'etag', metamodelId: 'people', name: 'People editor', compiledAt: '2026-07-28T12:00:00Z',
        representationsById: { main: representation('main', 'Main diagram'), audit: representation('audit', 'Audit view') }, formsById: {}, formsByTargetClassId: {}, capabilityManifest: { capabilities: ['MODEL_EDITING'] }, runtimePolicy: { validationTriggers:['MANUAL'], autosave:true, recoverLatestSession:true, publishEvents:true, eventFlowsEnabled:false, gamificationEnabled:false, maximumOpenRepresentations:2, operationTimeoutMillis:30000 }, annotations: {}
    }
};

describe('compiled editor runtime preview', () => {
    it('explains that compilation does not mutate the source definition', () => {
        render(<EditorRuntimePreview result={null} metamodel={metamodel} compiling={false} onCompile={vi.fn()} />);
        expect(screen.getByText(/never changes the source definition/i)).toBeInTheDocument();
    });

    it('offers runtime compilation when no plan exists', () => {
        const compile = vi.fn();
        render(<EditorRuntimePreview result={null} metamodel={metamodel} compiling={false} onCompile={compile} />);
        fireEvent.click(screen.getByRole('button', { name: 'Compile runtime' }));
        expect(compile).toHaveBeenCalledOnce();
    });

    it('shows compiled mappings with high-level class descriptions', () => {
        render(<EditorRuntimePreview result={result} metamodel={metamodel} compiling={false} onCompile={vi.fn()} />);
        expect(screen.getByText('Represents Person instances.')).toBeInTheDocument();
        expect(screen.queryByText('class:Person')).not.toBeInTheDocument();
    });

    it('switches between compiled representations', () => {
        render(<EditorRuntimePreview result={result} metamodel={metamodel} compiling={false} onCompile={vi.fn()} />);
        fireEvent.click(screen.getByRole('button', { name: /Audit view/i }));
        expect(screen.getByText('Audit view', { selector: '.editor-runtime-palette-preview strong' })).toBeInTheDocument();
    });
});
