import { afterEach, describe, expect, it, vi } from 'vitest';

import { addModelElement, createModel, deleteSavedModel, listMetamodels, loadEditorBootstrap, saveModel, setModelAttribute, setModelReference } from './editorApi';
describe('editorApi', () => {
    afterEach(() => {
        vi.unstubAllGlobals();
    });

    it('loads registered metamodels', async () => {
        const json = vi.fn().mockResolvedValue([{ id: 'sample', fileName: 'sample.ecore', rootPackageCount: 1 }]);
        const fetchMock = vi.fn().mockResolvedValue({ ok: true, json });
        vi.stubGlobal('fetch', fetchMock);

        const result = await listMetamodels();

        expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/metamodels');
        expect(result[0].id).toBe('sample');
    });

    it('loads editor bootstrap information', async () => {
        const json = vi.fn().mockResolvedValue({ metamodelId: 'sample', fileName: 'sample.ecore', palette: [], descriptor: {} });
        const fetchMock = vi.fn().mockResolvedValue({ ok: true, json });
        vi.stubGlobal('fetch', fetchMock);

        const result = await loadEditorBootstrap('sample');

        expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/editor/bootstrap/sample');
        expect(result.metamodelId).toBe('sample');
    });

    it('rejects unsuccessful HTTP responses', async () => {
        const fetchMock = vi.fn().mockResolvedValue({ ok: false, status: 404 });
        vi.stubGlobal('fetch', fetchMock);

        await expect(loadEditorBootstrap('unknown')).rejects.toThrow('The requested item could not be found.');
    });

    it('creates a semantic model', async () => {
        const json = vi.fn().mockResolvedValue({ id: 'model-1', rootElementId: 'element-1', elements: [], references: [] });
        const fetchMock = vi.fn().mockResolvedValue({ ok: true, json });
        vi.stubGlobal('fetch', fetchMock);

        const result = await createModel('sample', 'sample#//Model');

        expect(fetchMock).toHaveBeenCalledWith('/api/models', expect.objectContaining({ method: 'POST' }));
        expect(result.id).toBe('model-1');
    });

    it('adds a contained element', async () => {
        const json = vi.fn().mockResolvedValue({ id: 'model-1', elements: [{ id: 'element-2' }] });
        const fetchMock = vi.fn().mockResolvedValue({ ok: true, json });
        vi.stubGlobal('fetch', fetchMock);

        const result = await addModelElement('model-1', 'element-1', 'elements', 'sample#//Node');

        expect(fetchMock).toHaveBeenCalledWith('/api/models/model-1/elements', expect.objectContaining({ method: 'POST' }));
        expect(result.elements[0].id).toBe('element-2');
    });

    it('updates an attribute', async () => {
        const json = vi.fn().mockResolvedValue({ id: 'model-1' });
        const fetchMock = vi.fn().mockResolvedValue({ ok: true, json });
        vi.stubGlobal('fetch', fetchMock);

        await setModelAttribute('model-1', 'element-2', 'name', 'Node A');

        expect(fetchMock).toHaveBeenCalledWith('/api/models/model-1/attributes', expect.objectContaining({ method: 'PUT' }));
    });

    it('creates a semantic reference', async () => {
        const json = vi.fn().mockResolvedValue({ id: 'model-1' });
        const fetchMock = vi.fn().mockResolvedValue({ ok: true, json });
        vi.stubGlobal('fetch', fetchMock);

        await setModelReference('model-1', 'element-2', 'target', 'element-3');

        expect(fetchMock).toHaveBeenCalledWith('/api/models/model-1/references', expect.objectContaining({ method: 'PUT' }));
    });

    it('saves a semantic model', async () => {
        const json = vi.fn().mockResolvedValue({ id: 'model-1', dirty: false });
        const fetchMock = vi.fn().mockResolvedValue({ ok: true, json });
        vi.stubGlobal('fetch', fetchMock);

        const result = await saveModel('model-1');

        expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/models/model-1/save'); expect((fetchMock.mock.calls[0]?.[1] as RequestInit).method).toBe('POST');
        expect(result.dirty).toBe(false);
    });

    it('deletes a saved XMI model', async () => {
        const fetchMock = vi.fn().mockResolvedValue({ ok: true, status: 204 });
        vi.stubGlobal('fetch', fetchMock);

        await deleteSavedModel('model-1.xmi');

        expect(fetchMock).toHaveBeenCalledWith('/api/models/files/model-1.xmi', expect.objectContaining({ method: 'DELETE' }));
    });



    it('keeps the stable HTTP fallback when a response double has no body readers', async () => {
        const fetchMock = vi.fn().mockResolvedValue({ ok: false, status: 503 });
        vi.stubGlobal('fetch', fetchMock);

        await expect(loadEditorBootstrap('offline')).rejects.toThrow('The HYDRA service is temporarily unavailable.');
    });

});

