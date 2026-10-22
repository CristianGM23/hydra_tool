import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { UpdateSiteReleaseGovernancePanel } from './UpdateSiteReleaseGovernancePanel';
import type { CapabilitySemanticMergePreview, CapabilitySemanticMergeRequest } from './types';

const api = vi.hoisted(() => ({
    previewCapabilitySemanticMerge: vi.fn(), applyCapabilitySemanticMerge: vi.fn(),
    compareCapabilityPerformanceReports: vi.fn(), createOfflineP2Mirror: vi.fn(), downloadOfflineP2Mirror: vi.fn(),
    downloadReleaseGateHtml: vi.fn(), evaluateReleaseGate: vi.fn(), runAdapterBench: vi.fn()
}));
vi.mock('./api', () => api);

const pkg = { manifest: null, canonicalDocuments: {}, resources: {} };
function preview(unresolved: string[], keys = ['layer#document', 'layer/description']): CapabilitySemanticMergePreview {
    return { previewId: 'p', mergedPackage: pkg, keptElements: 0, recoveredElements: 0, mergedElements: 0, skippedElements: 0,
        items: keys.map(key => ({ key, documentPath: 'layer', jsonPointer: '', semanticKind: 'document', displayName: key,
            existingPresent: true, recoveredPresent: true, conflict: true, recommendedDecision: 'MERGE', selectedDecision: 'KEEP_EXISTING',
            dependencyReferences: [], explanation: 'differs' })),
        conflicts: keys.map(key => ({ key, type: 'VALUE', severity: 'WARNING', existingFingerprint: 'a', recoveredFingerprint: 'b', explanation: `${key} differs` })),
        dependencies: [], applicable: unresolved.length === 0, diagnostics: [], unresolvedConflictKeys: unresolved };
}
function lastRequest(mock: ReturnType<typeof vi.fn>): CapabilitySemanticMergeRequest {
    return mock.mock.calls[mock.mock.calls.length - 1][0] as CapabilitySemanticMergeRequest;
}

describe('UpdateSiteReleaseGovernancePanel semantic merge decisions', () => {
    beforeEach(() => { vi.clearAllMocks(); });

    it('never sends MERGE as a silent default and blocks applying while a conflict has no decision', async () => {
        api.previewCapabilitySemanticMerge.mockResolvedValue(preview(['layer#document', 'layer/description']));
        render(<UpdateSiteReleaseGovernancePanel />);
        fireEvent.click(screen.getByRole('button', { name: 'Preview semantic merge' }));

        await screen.findByText(/2 conflict\(s\): choose how to resolve each one/);
        const request = lastRequest(api.previewCapabilitySemanticMerge);
        expect(request.decisions).toEqual({});
        expect(request.defaultDecision).toBeUndefined();
        expect(screen.getByText(/2 conflict\(s\) still need a decision/)).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Apply through transaction' })).toBeDisabled();
    });

    it('sends one explicit decision per conflict, asks for a new preview after a change and only then allows applying', async () => {
        api.previewCapabilitySemanticMerge.mockResolvedValueOnce(preview(['layer#document', 'layer/description']))
            .mockResolvedValueOnce(preview([]));
        api.applyCapabilitySemanticMerge.mockResolvedValue({ preview: preview([]), transaction: {} });
        render(<UpdateSiteReleaseGovernancePanel />);
        fireEvent.click(screen.getByRole('button', { name: 'Preview semantic merge' }));
        await screen.findByText(/choose how to resolve each one/);

        fireEvent.change(screen.getByLabelText('Decision for layer#document'), { target: { value: 'MERGE' } });
        fireEvent.change(screen.getByLabelText('Decision for layer/description'), { target: { value: 'KEEP_EXISTING' } });
        expect(screen.getByText('Preview is out of date')).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Apply through transaction' })).toBeDisabled();

        fireEvent.click(screen.getByRole('button', { name: 'Preview semantic merge' }));
        await waitFor(() => expect(screen.getByText('Merge is applicable')).toBeInTheDocument());
        expect(lastRequest(api.previewCapabilitySemanticMerge).decisions).toEqual({ 'layer#document': 'MERGE', 'layer/description': 'KEEP_EXISTING' });

        const applyButton = screen.getByRole('button', { name: 'Apply through transaction' });
        expect(applyButton).toBeEnabled();
        fireEvent.click(applyButton);
        await waitFor(() => expect(api.applyCapabilitySemanticMerge).toHaveBeenCalledTimes(1));
        const applied = lastRequest(api.applyCapabilitySemanticMerge);
        expect(applied.persist).toBe(true);
        expect(applied.decisions).toEqual({ 'layer#document': 'MERGE', 'layer/description': 'KEEP_EXISTING' });
    });

    it('does not send a stale decision when applying: only decisions of elements in the latest preview', async () => {
        api.previewCapabilitySemanticMerge.mockResolvedValueOnce(preview(['layer#document', 'layer/description'])).mockResolvedValueOnce(preview([], ['layer#document']));
        api.applyCapabilitySemanticMerge.mockResolvedValue({ preview: preview([], ['layer#document']), transaction: {} });
        render(<UpdateSiteReleaseGovernancePanel />);
        fireEvent.click(screen.getByRole('button', { name: 'Preview semantic merge' }));
        await screen.findByText(/choose how to resolve each one/);
        fireEvent.change(screen.getByLabelText('Decision for layer#document'), { target: { value: 'MERGE' } });
        fireEvent.change(screen.getByLabelText('Decision for layer/description'), { target: { value: 'KEEP_EXISTING' } });
        // The user then resolves the whole layer instead: the nested element disappears from the next preview.
        fireEvent.change(screen.getByLabelText('Decision for layer#document'), { target: { value: 'KEEP_EXISTING' } });
        fireEvent.click(screen.getByRole('button', { name: 'Preview semantic merge' }));
        await screen.findByText('Merge is applicable');

        fireEvent.click(screen.getByRole('button', { name: 'Apply through transaction' }));
        await waitFor(() => expect(api.applyCapabilitySemanticMerge).toHaveBeenCalledTimes(1));
        expect(lastRequest(api.applyCapabilitySemanticMerge).decisions).toEqual({ 'layer#document': 'KEEP_EXISTING' });
    });

    it('shows no decision controls when the merge has no conflicts', async () => {
        api.previewCapabilitySemanticMerge.mockResolvedValue({ ...preview([], []), applicable: true });
        render(<UpdateSiteReleaseGovernancePanel />);
        fireEvent.click(screen.getByRole('button', { name: 'Preview semantic merge' }));

        await screen.findByText('Merge is applicable');
        expect(screen.queryByRole('group', { name: 'Merge conflicts' })).not.toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Apply through transaction' })).toBeEnabled();
    });
});
