import { beforeEach, describe, expect, it, vi } from 'vitest';
import { invokeModelOperation, loadOperationRuntimeCatalog, undoModelOperation } from './operationRuntimeApi';

const fetchMock = vi.fn();
beforeEach(() => { fetchMock.mockReset(); vi.stubGlobal('fetch', fetchMock); });

describe('operation runtime API', () => {
    it('loads, invokes and undoes through canonical routes', async () => {
        fetchMock.mockResolvedValueOnce(new Response(JSON.stringify({ metamodelId: 'shop', operations: [] }), { status: 200, headers: { 'Content-Type': 'application/json' } }));
        await loadOperationRuntimeCatalog('shop');
        expect(fetchMock.mock.calls.at(-1)?.[0]).toBe('/api/canonical-metamodels/shop/operation-runtime');

        fetchMock.mockResolvedValueOnce(new Response(JSON.stringify({ operationId: 'op', document: { id: 'model' }, undoAvailable: true, messages: [] }), { status: 200, headers: { 'Content-Type': 'application/json' } }));
        await invokeModelOperation('model', 'op', { targetElementId: 'element', arguments: { value: 1 } });
        expect(fetchMock).toHaveBeenLastCalledWith('/api/models/model/operations/op', expect.objectContaining({ method: 'POST' }));

        fetchMock.mockResolvedValueOnce(new Response(JSON.stringify({ operationId: 'undo', document: { id: 'model' }, undoAvailable: false, messages: [] }), { status: 200, headers: { 'Content-Type': 'application/json' } }));
        await undoModelOperation('model');
        expect(fetchMock.mock.calls.at(-1)?.[0]).toBe('/api/models/model/operations/undo'); expect((fetchMock.mock.calls.at(-1)?.[1] as RequestInit).method).toBe('POST');
    });
});
