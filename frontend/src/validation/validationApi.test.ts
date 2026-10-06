import { afterEach, describe, expect, it, vi } from 'vitest';
import { checkValidationModel, createValidationModel, deleteValidationModel, loadValidationModel, replaceValidationModel } from './validationApi';
import type { ValidationModel } from './types';

const model: ValidationModel = { schemaVersion: '1.0', id: 'library-validations', metamodelId: 'library', expressionModelId: 'library', actionModelId: 'library-actions', ruleSets: [], rules: [], importedSources: [], annotations: {} };
afterEach(() => vi.restoreAllMocks());

describe('canonical validation API', () => {
    it('loads documents and preserves ETag', async () => {
        vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify(model), { headers: { ETag: '"etag-1"' } })));
        await expect(loadValidationModel('library')).resolves.toEqual({ validationModel: model, etag: '"etag-1"' });
    });
    it('normalizes sparse validation documents and check reports at the HTTP boundary', async () => {
        const sparse = { schemaVersion: '1.0', id: model.id, metamodelId: model.metamodelId, expressionModelId: model.expressionModelId, actionModelId: model.actionModelId };
        vi.stubGlobal('fetch', vi.fn()
            .mockResolvedValueOnce(new Response(JSON.stringify(sparse), { headers: { ETag: '"etag-1"' } }))
            .mockResolvedValueOnce(new Response(JSON.stringify({ validationModelId: model.id }))));
        await expect(loadValidationModel('library')).resolves.toEqual({ validationModel: model, etag: '"etag-1"' });
        await expect(checkValidationModel('library')).resolves.toEqual({ validationModelId: model.id, diagnostics: [], rules: [] });
    });
    it('creates below the owning metamodel', async () => {
        const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ validationModel: model, checkReport: { validationModelId: model.id, diagnostics: [], rules: [] }, etag: '' }), { status: 201, headers: { ETag: '"etag-1"' } }));
        vi.stubGlobal('fetch', fetchMock);
        await createValidationModel(model);
        expect(fetchMock).toHaveBeenCalledWith('/api/canonical-metamodels/library/validations', expect.objectContaining({ method: 'POST' }));
    });
    it('sends If-Match for replacement and deletion', async () => {
        const fetchMock = vi.fn()
            .mockResolvedValueOnce(new Response(JSON.stringify({ validationModel: model, checkReport: { validationModelId: model.id, diagnostics: [], rules: [] }, etag: '"etag-2"' })))
            .mockResolvedValueOnce(new Response(null, { status: 204 }));
        vi.stubGlobal('fetch', fetchMock);
        await replaceValidationModel(model, '"etag-1"');
        await deleteValidationModel('library', '"etag-2"');
        expect(fetchMock).toHaveBeenNthCalledWith(1, '/api/canonical-metamodels/library/validations', expect.objectContaining({ method: 'PUT', headers: expect.any(Headers) }));
        expect(fetchMock).toHaveBeenNthCalledWith(2, '/api/canonical-metamodels/library/validations', expect.objectContaining({ method: 'DELETE', headers: expect.any(Headers) }));
        expect(new Headers(fetchMock.mock.calls[0]?.[1]?.headers).get('If-Match')).toBe('"etag-1"');
        expect(new Headers(fetchMock.mock.calls[1]?.[1]?.headers).get('If-Match')).toBe('"etag-2"');
    });
    it('uses a separate check endpoint', async () => {
        const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ validationModelId: model.id, diagnostics: [], rules: [] })));
        vi.stubGlobal('fetch', fetchMock);
        await checkValidationModel('library');
        expect(fetchMock).toHaveBeenCalledWith('/api/canonical-metamodels/library/validations/check', expect.objectContaining({ method: 'POST', headers: expect.any(Headers) }));
    });
});
