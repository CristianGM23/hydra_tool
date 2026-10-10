import { afterEach, describe, expect, it, vi } from 'vitest';
import { assessStandaloneReleaseReadiness, packageStandaloneDistribution, planStandaloneDistribution } from './api';
import type { StandaloneDistributionProfile } from './types';

const profile: StandaloneDistributionProfile = { productName: 'People Editor', version: '1.0.0', targets: ['PWA'], offlineEnabled: true, fileAssociations: ['.xmi'], updateChannel: 'stable' };

describe('standalone distribution API', () => {
    afterEach(() => vi.unstubAllGlobals());
    it('plans deterministic artifacts below a server-owned safe root', async () => {
        const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify([{ path: 'public/manifest.webmanifest', mediaType: 'application/manifest+json', content: '{}' }]), { status: 200, headers: { 'Content-Type': 'application/json' } }));
        vi.stubGlobal('fetch', fetchMock);
        await expect(planStandaloneDistribution({ editorDirectory: 'people-editor', profile })).resolves.toHaveLength(1);
        expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toMatchObject({ editorDirectory: 'people-editor', profile: { targets: ['PWA'] } });
    });

    it('calls the release readiness gate without triggering packaging', async () => {
        const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ valid: false, diagnostics: [{ code: 'BUILT_ENTRY', severity: 'ERROR', path: 'dist/index.html', message: 'Missing' }], metadata: {} }), { status: 200, headers: { 'Content-Type': 'application/json' } }));
        vi.stubGlobal('fetch', fetchMock);
        await expect(assessStandaloneReleaseReadiness({ editorDirectory: 'people-editor', profile })).resolves.toMatchObject({ valid: false });
        expect(fetchMock.mock.calls[0][0]).toContain('/release-readiness');
    });

    it('uses the separate verified release-source endpoint', async () => {
        const fetchMock = vi.fn().mockResolvedValue(new Response(Uint8Array.from([0x50, 0x4b]), { status: 200, headers: { 'Content-Disposition': 'attachment; filename="editor-release-source.zip"', 'X-HYDRA-Distribution-SHA256': 'hash' } }));
        vi.stubGlobal('fetch', fetchMock);
        const archive = await packageStandaloneDistribution({ editorDirectory: 'people-editor', profile }, 'release-source');
        expect(archive.fileName).toBe('editor-release-source.zip');
        expect(fetchMock.mock.calls[0][0]).toContain('/package-release-source');
    });

    it('downloads compiled web assets from the separate deployment endpoint', async () => {
        const fetchMock = vi.fn().mockResolvedValue(new Response(Uint8Array.from([0x50, 0x4b]), { status: 200,
            headers: { 'Content-Disposition': 'attachment; filename="people-web.zip"', 'X-HYDRA-Distribution-SHA256': 'webhash' } }));
        vi.stubGlobal('fetch', fetchMock);
        const result = await packageStandaloneDistribution({ editorDirectory: 'people-editor', profile }, 'web');
        expect(result).toMatchObject({ fileName: 'people-web.zip', sha256: 'webhash' });
        expect(fetchMock.mock.calls[0][0]).toContain('/package-deployable-web');
    });

    it('returns the server filename and package checksum', async () => {
        const fetchMock = vi.fn().mockResolvedValue(new Response(Uint8Array.from([0x50, 0x4b, 0x03, 0x04]), { status: 200, headers: { 'Content-Disposition': 'attachment; filename="people-editor.zip"', 'X-HYDRA-Distribution-SHA256': 'abc123' } }));
        vi.stubGlobal('fetch', fetchMock);
        const packaged = await packageStandaloneDistribution({ editorDirectory: 'people-editor', profile });
        expect(packaged).toMatchObject({ fileName: 'people-editor.zip', sha256: 'abc123' });
        expect(packaged.blob.size).toBe(4);
    });
});
