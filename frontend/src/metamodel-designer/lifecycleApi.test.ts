import { afterEach, describe, expect, it, vi } from 'vitest';

import { deleteMetamodel, getMetamodelExportUrl, listMetamodelExportFormats, loadMetamodelDeletionImpact } from './api';

/**
 * Comprueba las operaciones HTTP de eliminación y exportación.
 */
describe('metamodel lifecycle API', () => {
    afterEach(() => {
        vi.unstubAllGlobals();
    });

    /**
     * Comprueba la consulta del impacto.
     */
    it('loads deletion impact', async () => {
        const fetchMock = vi.fn().mockResolvedValue({ ok: true, json: vi.fn().mockResolvedValue({ metamodelId: 'company' }) });
        vi.stubGlobal('fetch', fetchMock);

        await loadMetamodelDeletionImpact('company');

        expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/canonical-metamodels/company/deletion-impact');
    });

    /**
     * Comprueba la confirmación sin introducir identificadores.
     */
    it('deletes using query options', async () => {
        const fetchMock = vi.fn().mockResolvedValue({ ok: true, json: vi.fn().mockResolvedValue({ metamodelId: 'company', deletedFiles: [] }) });
        vi.stubGlobal('fetch', fetchMock);

        await deleteMetamodel('company', true, false);

        expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/canonical-metamodels/company/lifecycle?deleteNotations=true&deleteExports=false'); expect((fetchMock.mock.calls[0]?.[1] as RequestInit).method).toBe('DELETE');
    });

    /**
     * Comprueba formatos y URL de descarga.
     */
    it('lists formats and builds export URL', async () => {
        const fetchMock = vi.fn().mockResolvedValue({ ok: true, json: vi.fn().mockResolvedValue([]) });
        vi.stubGlobal('fetch', fetchMock);

        await listMetamodelExportFormats('company');

        expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/metamodels/company/export-formats');
        expect(getMetamodelExportUrl('company', 'xtext')).toBe('/api/metamodels/company/exports/xtext');
    });
});
