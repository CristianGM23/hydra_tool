import { afterEach, describe, expect, it, vi } from 'vitest';

import { downloadMetamodelExport, downloadMetamodelExportBundle } from './exportApi';

const options = {
    includeLayout: true,
    includeStyles: true,
    includeWarnings: true,
    transparentBackground: false,
    imageScale: 2,
    codePackageName: ''
};

/**
 * Crea una respuesta de descarga compatible con jsdom.
 *
 * @param fileName contiene el nombre del archivo.
 * @param content contiene el contenido simulado.
 * @returns una respuesta HTTP simulada.
 */
function createDownloadResponse(fileName: string, content: string): Response {
    return {
        ok: true,
        status: 200,
        headers: {
            get: (name: string) => name.toLowerCase() === 'content-disposition' ? `attachment; filename="${fileName}"` : null
        },
        blob: vi.fn().mockResolvedValue(new Blob([content]))
    } as unknown as Response;
}

describe('configurable metamodel export API', () => {
    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('posts an individual export request', async () => {
        Object.defineProperty(URL, 'createObjectURL', { configurable: true, value: vi.fn(() => 'blob:test') });
        Object.defineProperty(URL, 'revokeObjectURL', { configurable: true, value: vi.fn() });
        vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined);
        const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(createDownloadResponse('sample.xml', 'xml'));

        await downloadMetamodelExport('sample', { format: 'xml', options });

        expect(fetchMock).toHaveBeenCalledWith('/api/metamodels/sample/exports', expect.objectContaining({ method: 'POST', body: JSON.stringify({ format: 'xml', options }) }));
    });

    it('posts selected formats for a bundle', async () => {
        Object.defineProperty(URL, 'createObjectURL', { configurable: true, value: vi.fn(() => 'blob:test') });
        Object.defineProperty(URL, 'revokeObjectURL', { configurable: true, value: vi.fn() });
        vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined);
        const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(createDownloadResponse('sample-exports.zip', 'zip'));

        await downloadMetamodelExportBundle('sample', { formats: ['xml', 'svg'], options });

        expect(fetchMock).toHaveBeenCalledWith('/api/metamodels/sample/exports/bundle', expect.objectContaining({ method: 'POST', body: JSON.stringify({ formats: ['xml', 'svg'], options }) }));
    });
});
