import { afterEach, describe, expect, it, vi } from 'vitest';
import { compileValidationPlan, downloadValidationDrawio, runModelValidation } from './validationApi';
import type { ValidationExecutionRequest } from './types';

const request: ValidationExecutionRequest = {
    mode: 'FULL',
    model: { schemaVersion: '1.0', id: 'people-model', metamodelId: 'people', rootElementIds: [], elements: [], relations: [], annotations: {} },
    selectionElementIds: [],
    changeSet: { elementIds: [], classIds: [], attributeIds: [], relationIds: [], operationIds: [], modelChanged: false },
    variables: {}
};

afterEach(() => vi.restoreAllMocks());

describe('validation runtime API', () => {
    it('compiles the stored validation definition', async () => {
        const response = { plan: { id: 'people-plan' }, diagnostics: [], cacheHit: false };
        const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify(response)));
        vi.stubGlobal('fetch', fetchMock);
        await compileValidationPlan('people');
        expect(fetchMock).toHaveBeenCalledWith('/api/canonical-metamodels/people/validations/runtime/compile', expect.objectContaining({ method: 'POST', headers: expect.any(Headers) }));
    });

    it('posts a neutral model document for execution', async () => {
        const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ modelId: 'people-model', diagnostics: [] })));
        vi.stubGlobal('fetch', fetchMock);
        await runModelValidation('people', request);
        expect(fetchMock).toHaveBeenCalledWith('/api/canonical-metamodels/people/validations/runtime/validate', expect.objectContaining({ method: 'POST', body: JSON.stringify(request) }));
    });

    it('downloads definition and report drawio projections', async () => {
        Object.defineProperty(URL, 'createObjectURL', { configurable: true, value: vi.fn(() => 'blob:validation') });
        Object.defineProperty(URL, 'revokeObjectURL', { configurable: true, value: vi.fn() });
        vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined);
        const fetchMock = vi.fn().mockImplementation(async () => new Response('<mxfile/>'));
        vi.stubGlobal('fetch', fetchMock);
        await downloadValidationDrawio('people', 'definition');
        await downloadValidationDrawio('people', 'report', request);
        expect(fetchMock).toHaveBeenNthCalledWith(1, '/api/canonical-metamodels/people/validations/runtime/drawio/definition', expect.objectContaining({ method: 'GET', headers: expect.any(Headers) }));
        expect(fetchMock).toHaveBeenNthCalledWith(2, '/api/canonical-metamodels/people/validations/runtime/drawio/report', expect.objectContaining({ method: 'POST', body: JSON.stringify(request) }));
    });
});
