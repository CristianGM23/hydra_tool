import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { UpdateSiteLayerMergePanel } from './UpdateSiteLayerMergePanel';
import type { UpdateSiteImportAnalysis, UpdateSiteLayerMergePreview, UpdateSiteLayerMergeRequest,
    UpdateSiteLayerMergeResult, UpdateSiteLayerSelection } from './types';

const api = vi.hoisted(() => ({ mergeUpdateSiteLayers: vi.fn(), mergeRemoteSnapshotLayers: vi.fn(), rollbackUpdateSiteMerge: vi.fn() }));
const editorApi = vi.hoisted(() => ({ listMetamodels: vi.fn() }));
const definitionApi = vi.hoisted(() => ({ listEditorDefinitions: vi.fn() }));
vi.mock('./api', () => api);
vi.mock('../api/editorApi', () => editorApi);
vi.mock('../editor-definition/api', () => definitionApi);

function result(unresolved: string[], persisted = false): UpdateSiteLayerMergeResult {
    const items = [
        { key: 'tool:save', kind: 'tool', displayName: 'Save tool', existingPresent: true, recoveredPresent: true, conflict: true,
            recommendedDecision: 'KEEP_EXISTING' as const, selectedDecision: 'KEEP_EXISTING' as const, explanation: 'Same ID, different content.' },
        { key: 'tool:new', kind: 'tool', displayName: 'New tool', existingPresent: false, recoveredPresent: true, conflict: false,
            recommendedDecision: 'USE_RECOVERED' as const, selectedDecision: 'USE_RECOVERED' as const, explanation: 'New element.' }];
    const preview: UpdateSiteLayerMergePreview = { transactionId: 'merge-1', targetMetamodelId: 'library', targetEditorId: 'main', originalEtag: 'e1',
        mergedEditorDefinition: {}, checkReport: { editorDefinitionId: 'main', diagnostics: [] }, items, keptElements: 1, recoveredElements: 1,
        skippedElements: 0, applicable: unresolved.length === 0, diagnostics: [], unresolvedConflictKeys: unresolved };
    return { preview, persisted, editorEtag: persisted ? 'e2' : '', rollbackAvailable: persisted, diagnostics: [] };
}
function lastRequest(): UpdateSiteLayerMergeRequest { return api.mergeUpdateSiteLayers.mock.calls[api.mergeUpdateSiteLayers.mock.calls.length - 1][1] as UpdateSiteLayerMergeRequest; }

async function renderReady(): Promise<void> {
    editorApi.listMetamodels.mockResolvedValue([{ id: 'library', fileName: 'library.metamodel.json', rootPackageCount: 1 }]);
    definitionApi.listEditorDefinitions.mockResolvedValue([{ id: 'main', name: 'Main editor', representationCount: 1, toolCount: 2, etag: 'e1' }]);
    render(<UpdateSiteLayerMergePanel files={[new File(['x'], 'site.zip')]} analysis={{ selectedCandidateId: 'c1' } as UpdateSiteImportAnalysis}
        selection={{} as UpdateSiteLayerSelection} />);
    await waitFor(() => expect(editorApi.listMetamodels).toHaveBeenCalled());
    const metamodel = await screen.findByLabelText('Destination metamodel');
    await waitFor(() => expect(metamodel).not.toBeDisabled());
    fireEvent.change(metamodel, { target: { value: 'library' } });
    await waitFor(() => expect(definitionApi.listEditorDefinitions).toHaveBeenCalledWith('library'));
    const editor = screen.getByLabelText('Destination editor');
    await waitFor(() => expect(editor).not.toBeDisabled());
    fireEvent.change(editor, { target: { value: 'main' } });
}

describe('UpdateSiteLayerMergePanel collision decisions', () => {
    beforeEach(() => { vi.clearAllMocks(); });

    it('has no default collision decision: nothing is sent unless the user chooses it, and Apply stays disabled until every collision is decided', async () => {
        api.mergeUpdateSiteLayers.mockResolvedValue(result(['tool:save']));
        await renderReady();
        expect(screen.queryByLabelText('Default collision decision')).not.toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Apply ETag-protected merge' })).toBeDisabled();

        fireEvent.click(screen.getByRole('button', { name: 'Preview selective merge' }));
        await screen.findByText(/1 collision\(s\) still need an explicit decision/);

        expect(lastRequest().decisions).toEqual({});
        expect(lastRequest().defaultDecision).toBeUndefined();
        expect(screen.getByRole('button', { name: 'Apply ETag-protected merge' })).toBeDisabled();
    });

    it('sends the explicit decision, requires a new preview after changing it and then allows applying', async () => {
        api.mergeUpdateSiteLayers.mockResolvedValueOnce(result(['tool:save'])).mockResolvedValueOnce(result([]))
            .mockResolvedValueOnce(result([], true));
        await renderReady();
        fireEvent.click(screen.getByRole('button', { name: 'Preview selective merge' }));
        await screen.findByText(/still need an explicit decision/);

        fireEvent.change(screen.getByLabelText('Merge decision for Save tool'), { target: { value: 'USE_RECOVERED' } });
        expect(screen.getByText(/preview the merge again before applying it/)).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Apply ETag-protected merge' })).toBeDisabled();

        fireEvent.click(screen.getByRole('button', { name: 'Preview selective merge' }));
        await screen.findByText('Every collision has an explicit decision.');
        expect(lastRequest().decisions).toEqual({ 'tool:save': 'USE_RECOVERED' });
        const apply = screen.getByRole('button', { name: 'Apply ETag-protected merge' });
        expect(apply).toBeEnabled();

        fireEvent.click(apply);
        await waitFor(() => expect(api.mergeUpdateSiteLayers).toHaveBeenCalledTimes(3));
        expect(lastRequest().persist).toBe(true);
        expect(lastRequest().decisions).toEqual({ 'tool:save': 'USE_RECOVERED' });
    });
});
