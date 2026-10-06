import { afterEach, describe, expect, it, vi } from 'vitest';
import { packageStandaloneDistribution, planStandaloneDistribution } from './api';
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

    it('returns the server filename and package checksum', async () => {
        const fetchMock = vi.fn().mockResolvedValue(new Response(Uint8Array.from([0x50, 0x4b, 0x03, 0x04]), { status: 200, headers: { 'Content-Disposition': 'attachment; filename="people-editor.zip"', 'X-HYDRA-Distribution-SHA256': 'abc123' } }));
        vi.stubGlobal('fetch', fetchMock);
        const packaged = await packageStandaloneDistribution({ editorDirectory: 'people-editor', profile });
        expect(packaged).toMatchObject({ fileName: 'people-editor.zip', sha256: 'abc123' });
        expect(packaged.blob.size).toBe(4);
    });
});
