import { afterEach, describe, expect, it, vi } from 'vitest';
import { checkActionModel, createActionModel, deleteActionModel, loadActionModel, replaceActionModel } from './actionApi';
import type { ActionModel } from './types';

const model: ActionModel = { schemaVersion: '1.0', id: 'library-actions', metamodelId: 'library', expressionModelId: 'library', actions: [], annotations: {} };
afterEach(() => vi.restoreAllMocks());

describe('canonical action API', () => {
    it('loads documents and preserves ETag', async () => {
        vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify(model), { headers: { ETag: '"etag-1"' } })));
        await expect(loadActionModel('library')).resolves.toEqual({ actionModel: model, etag: '"etag-1"' });
    });

    it('restores omitted empty collections returned by canonical JSON serialization', async () => {
        const sparse = { schemaVersion: '1.0', id: model.id, metamodelId: model.metamodelId, expressionModelId: model.expressionModelId };
        vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify(sparse), { headers: { ETag: '"etag-1"' } })));
        await expect(loadActionModel('library')).resolves.toEqual({ actionModel: model, etag: '"etag-1"' });
    });
    it('normalizes a saved empty action document before it reaches the designer state', async () => {
        const sparse = { schemaVersion: '1.0', id: model.id, metamodelId: model.metamodelId, expressionModelId: model.expressionModelId };
        vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ actionModel: sparse, checkReport: { actionModelId: model.id } }), { status: 201 })));
        const result = await createActionModel(model);
        expect(result.actionModel.actions).toEqual([]);
        expect(result.actionModel.annotations).toEqual({});
        expect(result.checkReport.actions).toEqual([]);
        expect(result.checkReport.diagnostics).toEqual([]);
    });
    it('creates below the owning metamodel', async () => {
        const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ actionModel: model, checkReport: { actionModelId: model.id, diagnostics: [], actions: [] }, etag: '' }), { status: 201, headers: { ETag: '"etag-1"' } }));
        vi.stubGlobal('fetch', fetchMock);
        await createActionModel(model);
        expect(fetchMock).toHaveBeenCalledWith('/api/canonical-metamodels/library/actions', expect.objectContaining({ method: 'POST' }));
    });
    it('sends If-Match for replacement and deletion', async () => {
        const fetchMock = vi.fn()
            .mockResolvedValueOnce(new Response(JSON.stringify({ actionModel: model, checkReport: { actionModelId: model.id, diagnostics: [], actions: [] }, etag: '"etag-2"' })))
            .mockResolvedValueOnce(new Response(null, { status: 204 }));
        vi.stubGlobal('fetch', fetchMock);
        await replaceActionModel(model, '"etag-1"');
        await deleteActionModel('library', '"etag-2"');
        expect(fetchMock).toHaveBeenNthCalledWith(1, '/api/canonical-metamodels/library/actions', expect.objectContaining({ method: 'PUT', headers: expect.any(Headers) }));
        expect(fetchMock).toHaveBeenNthCalledWith(2, '/api/canonical-metamodels/library/actions', expect.objectContaining({ method: 'DELETE', headers: expect.any(Headers) }));
        expect(new Headers(fetchMock.mock.calls[0]?.[1]?.headers).get('If-Match')).toBe('"etag-1"');
        expect(new Headers(fetchMock.mock.calls[1]?.[1]?.headers).get('If-Match')).toBe('"etag-2"');
    });
    it('uses a separate check endpoint', async () => {
        const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ actionModelId: model.id, diagnostics: [], actions: [] })));
        vi.stubGlobal('fetch', fetchMock);
        await checkActionModel('library');
        expect(fetchMock).toHaveBeenCalledWith('/api/canonical-metamodels/library/actions/check', expect.objectContaining({ method: 'POST', headers: expect.any(Headers) }));
    });
    it('normalizes sparse check reports before the workbench iterates them', async () => {
        vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ actionModelId: model.id }))));
        await expect(checkActionModel('library')).resolves.toEqual({ actionModelId: model.id, diagnostics: [], actions: [] });
    });
});
