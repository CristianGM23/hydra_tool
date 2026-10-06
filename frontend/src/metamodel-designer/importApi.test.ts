import { afterEach, describe, expect, it, vi } from 'vitest';

import { listMetamodelImportFormats, previewMetamodelImport } from './importApi';

const options = { formatId: 'auto', targetId: '', overwrite: false, preserveLayout: true, preserveStyles: true, strict: false };

describe('metamodel import API', () => {
    afterEach(() => vi.restoreAllMocks());

    it('loads import format descriptors', async () => {
        const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue({ ok: true, json: vi.fn().mockResolvedValue([{ id: 'ecore' }]) } as unknown as Response);
        const result = await listMetamodelImportFormats();
        expect(result).toEqual([{ id: 'ecore' }]);
        expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/metamodel-imports/formats');
    });

    it('posts a multipart preview with options', async () => {
        const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue({ ok: true, json: vi.fn().mockResolvedValue({ detectedFormat: 'json' }) } as unknown as Response);
        await previewMetamodelImport(new File(['{}'], 'company.json'), options);
        const [url, init] = fetchMock.mock.calls[0];
        expect(String(url)).toContain('/api/metamodel-imports/preview?');
        expect(String(url)).toContain('preserveLayout=true');
        expect(init).toEqual(expect.objectContaining({ method: 'POST', body: expect.any(FormData) }));
    });
});
