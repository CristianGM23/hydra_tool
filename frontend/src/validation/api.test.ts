import { afterEach, describe, expect, it, vi } from 'vitest';
import {
    checkExpressionModel,
    createExpressionModel,
    deleteExpressionModel,
    evaluateExpression,
    loadExpressionModel,
    replaceExpressionModel
} from './api';
import type { CanonicalModelDocument, ExpressionModel } from './types';

const model: ExpressionModel = {
    schemaVersion: '1.0', id: 'library', metamodelId: 'library', annotations: {}, expressions: []
};

const instance: CanonicalModelDocument = {
    schemaVersion: '1.0', id: 'sample', metamodelId: 'library', rootElementIds: [], elements: [], relations: [], annotations: {}
};

afterEach(() => vi.restoreAllMocks());

describe('canonical expression API', () => {
    it('reads the ETag returned with the document', async () => {
        vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify(model), { headers: { ETag: '"etag-1"' } })));
        await expect(loadExpressionModel('library')).resolves.toEqual({ expressionModel: model, etag: '"etag-1"' });
    });

    it('encodes the metamodel identifier in the route', async () => {
        const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify(model), { headers: { ETag: '"etag-1"' } }));
        vi.stubGlobal('fetch', fetchMock);
        await loadExpressionModel('library value');
        expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/canonical-metamodels/library%20value/expressions');
    });

    it('creates the expression model below its canonical metamodel', async () => {
        const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ expressionModel: model, checkReport: { expressionModelId: 'library', diagnostics: [], expressions: [] }, etag: '' }), { status: 201, headers: { ETag: '"etag-1"' } }));
        vi.stubGlobal('fetch', fetchMock);
        await expect(createExpressionModel(model)).resolves.toMatchObject({ etag: '"etag-1"' });
        expect(fetchMock).toHaveBeenCalledWith('/api/canonical-metamodels/library/expressions', expect.objectContaining({ method: 'POST' }));
    });

    it('sends If-Match when replacing', async () => {
        const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ expressionModel: model, checkReport: { expressionModelId: 'library', diagnostics: [], expressions: [] }, etag: '"etag-2"' })));
        vi.stubGlobal('fetch', fetchMock);
        await replaceExpressionModel(model, '"etag-1"');
        expect(fetchMock).toHaveBeenCalledWith('/api/canonical-metamodels/library/expressions', expect.objectContaining({ method: 'PUT', headers: expect.any(Headers) }));
        expect(new Headers(fetchMock.mock.calls[0]?.[1]?.headers).get('If-Match')).toBe('"etag-1"');
    });

    it('checks and evaluates expressions through separate endpoints', async () => {
        const fetchMock = vi.fn()
            .mockResolvedValueOnce(new Response(JSON.stringify({ expressionModelId: 'library', diagnostics: [], expressions: [] })))
            .mockResolvedValueOnce(new Response(JSON.stringify({ expressionId: 'expression:one', valueType: { kind: 'BOOLEAN' }, value: true })));
        vi.stubGlobal('fetch', fetchMock);
        await checkExpressionModel('library');
        await evaluateExpression('library', 'expression:one', instance, 'object:one');
        expect(fetchMock).toHaveBeenNthCalledWith(1, '/api/canonical-metamodels/library/expressions/check', expect.objectContaining({ method: 'POST', headers: expect.any(Headers) }));
        expect(fetchMock).toHaveBeenNthCalledWith(2, '/api/canonical-metamodels/library/expressions/expression%3Aone/evaluate', expect.objectContaining({ method: 'POST' }));
    });

    it('deletes with the current ETag', async () => {
        const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status: 204 }));
        vi.stubGlobal('fetch', fetchMock);
        await deleteExpressionModel('library', '"etag-1"');
        expect(fetchMock).toHaveBeenCalledWith('/api/canonical-metamodels/library/expressions', expect.objectContaining({ method: 'DELETE', headers: expect.any(Headers) }));
        expect(new Headers(fetchMock.mock.calls[0]?.[1]?.headers).get('If-Match')).toBe('"etag-1"');
    });

    it('surfaces stable actionable guidance instead of a raw server message', async () => {
        vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ message: 'Expression model has changed' }), { status: 412 })));
        await expect(loadExpressionModel('library')).rejects.toThrow('A required condition is no longer satisfied.');
    });
});
