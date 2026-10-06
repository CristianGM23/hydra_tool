import { beforeEach, describe, expect, it, vi } from 'vitest';
import { analyzeRemoteUpdateSite, analyzeUpdateSite, clearUpdateSiteCache, importDeclarativeAdapter,
    importRemoteSnapshotRecovery, importUpdateSite, loadUpdateSiteCacheMetrics, loadUpdateSiteExtensionAdapters,
    materializeRemoteSnapshot, materializeUpdateSiteLayers, mergeRemoteSnapshotLayers, mergeUpdateSiteLayers,
    previewDeclarativeAdapter, reconcileRemoteSnapshot, reconcileUpdateSite, rollbackUpdateSiteMaterialization,
    reviewRemoteUpdateSiteSnapshotLicenses, rollbackUpdateSiteMerge } from './api';
import type { UpdateSiteImportOptions } from './types';

vi.mock('../api/backendFetch', () => ({ backendFetch: vi.fn() }));
import { backendFetch } from '../api/backendFetch';

const options: UpdateSiteImportOptions = {
    targetId: 'library', overwrite: true, strict: false, candidateId: 'candidate-1',
    recoverPalettes: true, recoverRepresentations: false, recoverMappings: true,
    persistRecoveryPackage: true
};

describe('update-site import API', () => {
    beforeEach(() => vi.mocked(backendFetch).mockReset());

    it('preserves directory-relative paths and sends analysis switches as multipart fields', async () => {
        vi.mocked(backendFetch).mockResolvedValue(new Response(JSON.stringify({ analysisId: 'p2-1' }), { status: 200 }));
        const file = new File(['manifest'], 'MANIFEST.MF', { type: 'text/plain' });
        Object.defineProperty(file, 'webkitRelativePath', { value: 'plugins/model/META-INF/MANIFEST.MF' });

        await analyzeUpdateSite([file], options);

        const [url, request] = vi.mocked(backendFetch).mock.calls[0] ?? [];
        expect(url).toBe('/api/update-site-imports/analyze');
        expect(request?.method).toBe('POST');
        const form = request?.body as FormData;
        expect(form.get('targetId')).toBe('library');
        expect(form.get('candidateId')).toBe('candidate-1');
        expect(form.get('recoverRepresentations')).toBe('false');
        expect((form.get('files') as File).name).toBe('plugins/model/META-INF/MANIFEST.MF');
        expect(form.has('overwrite')).toBe(false);
    });

    it('includes persistence and overwrite controls only in the import request', async () => {
        vi.mocked(backendFetch).mockResolvedValue(new Response(JSON.stringify({ analysisId: 'p2-1' }), { status: 200 }));

        await importUpdateSite([new File(['site'], 'site.zip')], options);

        const form = vi.mocked(backendFetch).mock.calls[0]?.[1]?.body as FormData;
        expect(form.get('overwrite')).toBe('true');
        expect(form.get('persistRecoveryPackage')).toBe('true');
        expect(form.get('strict')).toBe('false');
    });
    it('uses explicit multipart requests for reconciliation and layer materialization', async () => {
        vi.mocked(backendFetch).mockImplementation(() => Promise.resolve(new Response(JSON.stringify({ applicable: true }), { status: 200 })));
        const file = new File(['site'], 'site.zip');

        await reconcileUpdateSite([file], { targetId: 'library', primaryCandidateId: 'ecore', conflictCandidateIds: { 'CLASS:Book': 'ecore' } });
        await materializeUpdateSiteLayers([file], {
            targetId: 'library', candidateId: 'ecore', editorId: 'library-editor', editorName: 'Library editor',
            strict: false, persist: false, selection: { paletteGroupIds: [], paletteToolIds: [], representationIds: [], mappingIds: [], semanticElementIds: [], explicit: true }
        });

        expect(vi.mocked(backendFetch).mock.calls[0]?.[0]).toBe('/api/update-site-imports/reconcile');
        const reconcileForm = vi.mocked(backendFetch).mock.calls[0]?.[1]?.body as FormData;
        expect(JSON.parse(String(reconcileForm.get('request')))).toMatchObject({ primaryCandidateId: 'ecore' });
        expect(vi.mocked(backendFetch).mock.calls[1]?.[0]).toBe('/api/update-site-imports/materialize');
        const materializeForm = vi.mocked(backendFetch).mock.calls[1]?.[1]?.body as FormData;
        expect(JSON.parse(String(materializeForm.get('request')))).toMatchObject({ editorId: 'library-editor', persist: false });
    });

    it('sends rollback identifiers as JSON without reuploading the repository', async () => {
        vi.mocked(backendFetch).mockResolvedValue(new Response(JSON.stringify({ editorDeleted: true, metamodelDeleted: true, diagnostics: [] }), { status: 200 }));

        await rollbackUpdateSiteMaterialization({ metamodelId: 'library', metamodelEtag: 'm1', editorId: 'library-editor', editorEtag: 'e1' });

        const [url, request] = vi.mocked(backendFetch).mock.calls[0] ?? [];
        expect(url).toBe('/api/update-site-imports/materialize/rollback');
        expect(request?.headers).toEqual({ 'Content-Type': 'application/json' });
        expect(JSON.parse(String(request?.body))).toEqual({ metamodelId: 'library', metamodelEtag: 'm1', editorId: 'library-editor', editorEtag: 'e1' });
    });

    it('loads the server-owned extension adapter catalog without accepting executable definitions', async () => {
        vi.mocked(backendFetch).mockResolvedValue(new Response(JSON.stringify([{ id: 'custom-palettes' }]), { status: 200 }));

        const result = await loadUpdateSiteExtensionAdapters();

        expect(vi.mocked(backendFetch)).toHaveBeenCalledWith('/api/update-site-imports/extension-adapters');
        expect(result).toEqual([{ id: 'custom-palettes' }]);
    });

    it('uses additive V3.6 endpoints for merge, remote analysis, adapters and cache metrics', async () => {
        vi.mocked(backendFetch).mockImplementation(() => Promise.resolve(new Response(JSON.stringify({}), { status: 200 })));
        const file = new File(['site'], 'site.zip');
        const selection = { paletteGroupIds: [], paletteToolIds: [], representationIds: [], mappingIds: [], semanticElementIds: [], explicit: true };

        await mergeUpdateSiteLayers([file], { targetMetamodelId: 'library', targetEditorId: 'editor', expectedEtag: 'e1',
            candidateId: 'candidate-1', strict: false, persist: false, selection, defaultDecision: 'KEEP_EXISTING', decisions: {} });
        await rollbackUpdateSiteMerge({ transactionId: 'merge-1', expectedCurrentEtag: 'e2' });
        await analyzeRemoteUpdateSite({ repositoryUrl: 'https://updates.example.org/', expectedSha256: '', maximumDepth: 2,
            useCache: true, options });
        await previewDeclarativeAdapter('{"schemaVersion":"1.0"}');
        await importDeclarativeAdapter('{"schemaVersion":"1.0"}', true);
        await loadUpdateSiteCacheMetrics();
        await clearUpdateSiteCache();

        expect(vi.mocked(backendFetch).mock.calls.map(call => call[0])).toEqual([
            '/api/update-site-imports/merge', '/api/update-site-imports/merge/rollback',
            '/api/update-site-imports/remote/analyze', '/api/update-site-imports/extension-adapters/preview',
            '/api/update-site-imports/extension-adapters/import?overwrite=true',
            '/api/update-site-imports/performance/cache', '/api/update-site-imports/performance/cache'
        ]);
        const mergeForm = vi.mocked(backendFetch).mock.calls[0]?.[1]?.body as FormData;
        expect(JSON.parse(String(mergeForm.get('request')))).toMatchObject({ expectedEtag: 'e1', persist: false });
        expect(vi.mocked(backendFetch).mock.calls[6]?.[1]?.method).toBe('DELETE');
    });


    it('uses verified snapshot endpoints without reuploading remote repository bytes', async () => {
        vi.mocked(backendFetch).mockImplementation(() => Promise.resolve(new Response(JSON.stringify({}), { status: 200 })));
        const selection = { paletteGroupIds: [], paletteToolIds: [], representationIds: [], mappingIds: [], semanticElementIds: [], explicit: true };
        const snapshotId = 'p2-1234567890abcdef12345678';
        await importRemoteSnapshotRecovery(snapshotId, options);
        await reconcileRemoteSnapshot(snapshotId, { targetId: 'library', primaryCandidateId: 'candidate-1', conflictCandidateIds: {} });
        await materializeRemoteSnapshot(snapshotId, { targetId: 'library', candidateId: 'candidate-1', editorId: 'editor',
            editorName: 'Editor', strict: false, persist: false, selection });
        await mergeRemoteSnapshotLayers(snapshotId, { targetMetamodelId: 'library', targetEditorId: 'editor',
            expectedEtag: '', candidateId: 'candidate-1', strict: false, persist: false, selection,
            defaultDecision: 'KEEP_EXISTING', decisions: {} });
        await reviewRemoteUpdateSiteSnapshotLicenses(snapshotId, [{ virtualPath: 'content.xml', author: 'Example',
            origin: 'https://updates.example.org/content.xml', license: 'EPL-2.0', obligations: 'Preserve notices',
            compatible: true, redistributionAllowed: true }]);

        expect(vi.mocked(backendFetch).mock.calls.map(call => call[0])).toEqual([
            `/api/update-site-imports/remote/snapshots/${snapshotId}/import`,
            `/api/update-site-imports/remote/snapshots/${snapshotId}/reconcile`,
            `/api/update-site-imports/remote/snapshots/${snapshotId}/materialize`,
            `/api/update-site-imports/remote/snapshots/${snapshotId}/merge`,
            `/api/update-site-imports/remote/snapshots/${snapshotId}/licenses`
        ]);
        for (const call of vi.mocked(backendFetch).mock.calls) {
            expect(call[1]?.headers).toEqual({ 'Content-Type': 'application/json' });
            expect(call[1]?.body).toEqual(expect.any(String));
        }
    });

});
