import { fireEvent, render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { MetamodelExportDialog } from './MetamodelExportDialog';
import { downloadMetamodelProjectExport, listMetamodelProjectExportFormats, loadMetamodelProjectExportArtifacts } from './projectExportApi';
import { loadMetamodelValidationStatus } from './validationApi';
import type { MetamodelProjectExportDescriptor } from './types';

vi.mock('./exportApi', () => ({ downloadMetamodelExport: vi.fn(), downloadMetamodelExportBundle: vi.fn() }));
vi.mock('./projectExportApi', () => ({ downloadMetamodelProjectExport: vi.fn().mockResolvedValue(undefined), listMetamodelProjectExportFormats: vi.fn(), loadMetamodelProjectExportArtifacts: vi.fn() }));
vi.mock('./validationApi', () => ({ loadMetamodelValidationStatus: vi.fn() }));

const ordinary = [{ id: 'ecore', displayName: 'Ecore', extension: '.ecore', category: 'SEMANTIC' as const }];
const projects: MetamodelProjectExportDescriptor[] = [
    { id: 'emf-eclipse', displayName: 'EMF Eclipse workspace', description: 'Model/Edit/Editor/Tests', technology: 'EMF', requiresEditorDefinition: false, validationRequired: true, extension: '.zip', defaultSettings: {} },
    { id: 'eclipse-glsp', displayName: 'Eclipse GLSP workspace', description: 'Client/server/web', technology: 'ECLIPSE_GLSP', requiresEditorDefinition: true, validationRequired: true, extension: '.zip', defaultSettings: { serverLanguage: 'java' } }
];

describe('complete metamodel project exports', () => {
    beforeEach(() => { vi.clearAllMocks(); vi.mocked(listMetamodelProjectExportFormats).mockResolvedValue(projects); vi.mocked(loadMetamodelProjectExportArtifacts).mockResolvedValue({ metamodelId: 'people', artifacts: [] }); });

    it('does not load complete-project metadata until that export scope is requested', async () => {
        vi.mocked(loadMetamodelValidationStatus).mockResolvedValue({ metamodelId: 'people', validationRequired: true, validated: true, current: true, stale: false, valid: true, generationReady: true, currentFingerprint: 'a', validatedFingerprint: 'a', validatedAt: '', summary: { errors: 0, warnings: 0, total: 0 }, message: 'ready' });
        render(<MetamodelExportDialog formats={ordinary} metamodelId="people" onClose={vi.fn()} />);
        expect(listMetamodelProjectExportFormats).not.toHaveBeenCalled();
        expect(loadMetamodelProjectExportArtifacts).not.toHaveBeenCalled();
        expect(loadMetamodelValidationStatus).not.toHaveBeenCalled();
        fireEvent.click(screen.getByRole('button', { name: 'Complete projects' }));
        await vi.waitFor(() => expect(listMetamodelProjectExportFormats).toHaveBeenCalledWith('people'));
    });

    it('blocks complete project generation and tells the user to validate first', async () => {
        vi.mocked(loadMetamodelValidationStatus).mockResolvedValue({ metamodelId: 'people', validationRequired: true, validated: false, current: false, stale: false, valid: false, generationReady: false, currentFingerprint: 'a', validatedFingerprint: '', validatedAt: '', summary: { errors: 0, warnings: 0, total: 0 }, message: 'This metamodel has not been validated yet.' });
        render(<MetamodelExportDialog formats={ordinary} metamodelId="people" onClose={vi.fn()} />);
        fireEvent.click(screen.getByRole('button', { name: 'Complete projects' }));
        expect(await screen.findByText('Metamodel validation is required before complete project generation.')).toBeTruthy();
        expect(screen.getAllByRole('button', { name: 'Generate complete ZIP' }).every(button => (button as HTMLButtonElement).disabled)).toBe(true);
    });

    it('enables validated project generation and forwards GLSP server configuration', async () => {
        vi.mocked(loadMetamodelValidationStatus).mockResolvedValue({ metamodelId: 'people', validationRequired: true, validated: true, current: true, stale: false, valid: true, generationReady: true, currentFingerprint: 'a', validatedFingerprint: 'a', validatedAt: '', summary: { errors: 0, warnings: 0, total: 0 }, message: 'ready' });
        render(<MetamodelExportDialog formats={ordinary} metamodelId="people" onClose={vi.fn()} />);
        fireEvent.click(screen.getByRole('button', { name: 'Complete projects' }));
        await screen.findByText('Generation gate passed.');
        fireEvent.click(screen.getAllByRole('button', { name: 'Generate complete ZIP' })[0]);
        await vi.waitFor(() => expect(downloadMetamodelProjectExport).toHaveBeenCalledWith('people', 'emf-eclipse', expect.objectContaining({ includeExistingLayers: true, selectionMode: 'ALL_COMPATIBLE', selectedArtifacts: [], editorId: '' })));
    });
    it('requires an explicit editor when more than one stored editor can feed a graphical target', async () => {
        vi.mocked(loadMetamodelValidationStatus).mockResolvedValue({ metamodelId: 'people', validationRequired: true, validated: true, current: true, stale: false, valid: true, generationReady: true, currentFingerprint: 'a', validatedFingerprint: 'a', validatedAt: '', summary: { errors: 0, warnings: 0, total: 0 }, message: 'ready' });
        vi.mocked(loadMetamodelProjectExportArtifacts).mockResolvedValue({ metamodelId: 'people', artifacts: [
            { kind: 'EDITOR_DEFINITION', documentId: 'editor-a', displayName: 'Editor · A', etag: 'a', editorDefinition: true },
            { kind: 'EDITOR_DEFINITION', documentId: 'editor-b', displayName: 'Editor · B', etag: 'b', editorDefinition: true }
        ] });
        render(<MetamodelExportDialog formats={ordinary} metamodelId="people" onClose={vi.fn()} />);
        fireEvent.click(screen.getByRole('button', { name: 'Complete projects' }));
        await screen.findByText('Generation gate passed.');
        const glsp = screen.getAllByRole('button', { name: 'Generate complete ZIP' })[1] as HTMLButtonElement;
        expect(glsp.disabled).toBe(true);
        fireEvent.change(screen.getByLabelText('Editor definition for complete project'), { target: { value: 'editor-b' } });
        expect(glsp.disabled).toBe(false);
    });

});
