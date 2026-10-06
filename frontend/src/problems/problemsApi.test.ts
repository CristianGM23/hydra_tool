import { beforeEach, describe, expect, it, vi } from 'vitest';
import { applyQuickFix, defaultLiveValidationRequest, loadDiagnosticQuickFixes, loadLatestValidationReport, previewQuickFix, redoQuickFix, undoQuickFix, validateLiveModel } from './problemsApi';

const fetchMock = vi.fn();
beforeEach(() => { fetchMock.mockReset(); vi.stubGlobal('fetch', fetchMock); });

function json(value: unknown): Response { return new Response(JSON.stringify(value), { status: 200, headers: { 'Content-Type': 'application/json' } }); }

describe('Problems and quick-fix API', () => {
    it('validates the live model through the V9 route', async () => {
        fetchMock.mockResolvedValue(json({ report: { diagnostics: [] }, delta: { created: [], unchanged: [], resolved: [] }, blocking: { blocked: false } }));
        const request = defaultLiveValidationRequest();
        await validateLiveModel('model one', request);
        expect(fetchMock).toHaveBeenCalledWith('/api/models/model%20one/validation-runtime/validate', expect.objectContaining({ method: 'POST', body: JSON.stringify(request) }));
    });

    it('loads the latest consolidated report', async () => {
        fetchMock.mockResolvedValue(json({ id: 'report:one', diagnostics: [] }));
        await loadLatestValidationReport('model one');
        expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/models/model%20one/validation-runtime/latest');
    });

    it('resolves quick fixes for one diagnostic', async () => {
        fetchMock.mockResolvedValue(json([{ actionId: 'action:fix' }]));
        await loadDiagnosticQuickFixes('model', 'diagnostic:one');
        expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/models/model/validation-runtime/problems/diagnostic%3Aone/quick-fixes');
    });

    it('previews without using the apply route', async () => {
        fetchMock.mockResolvedValue(json({ diagnosticId: 'd', actionId: 'a', previewDocument: {}, changes: {}, messages: [] }));
        await previewQuickFix('model', 'd', 'a', { arguments: {}, selectionElementIds: [] });
        expect(fetchMock).toHaveBeenCalledWith('/api/models/model/validation-runtime/problems/d/quick-fixes/a/preview', expect.objectContaining({ method: 'POST' }));
    });

    it('applies a selected quick fix', async () => {
        fetchMock.mockResolvedValue(json({ status: 'APPLIED_RESOLVED' }));
        await applyQuickFix('model', 'd', 'a', { arguments: { value: 1 }, selectionElementIds: ['e'] });
        expect(fetchMock).toHaveBeenCalledWith('/api/models/model/validation-runtime/problems/d/quick-fixes/a/apply', expect.objectContaining({ body: JSON.stringify({ arguments: { value: 1 }, selectionElementIds: ['e'] }) }));
    });

    it('uses separate undo and redo routes', async () => {
        fetchMock.mockResolvedValueOnce(json({ status: 'UNDONE' })).mockResolvedValueOnce(json({ status: 'REDONE' }));
        await undoQuickFix('model');
        await redoQuickFix('model');
        expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/models/model/validation-runtime/quick-fixes/undo');
        expect((fetchMock.mock.calls[0]?.[1] as RequestInit).method).toBe('POST');
        expect(fetchMock.mock.calls[1]?.[0]).toBe('/api/models/model/validation-runtime/quick-fixes/redo');
        expect((fetchMock.mock.calls[1]?.[1] as RequestInit).method).toBe('POST');
    });
});
