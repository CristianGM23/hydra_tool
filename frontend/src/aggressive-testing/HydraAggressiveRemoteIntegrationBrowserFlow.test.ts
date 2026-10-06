import { afterEach, describe, expect, it, vi } from 'vitest';

vi.mock('../remote-auth/api', () => ({
    remoteRequestHeaders: async (extra: HeadersInit = {}) => {
        const headers = new Headers(extra);
        headers.set('X-Hydra-Request-Token', 'aggressive-browser-token');
        return headers;
    },
}));

import { loadRemoteFileArtifact, saveRemoteLayerArtifact, testRemoteStorageConnection } from '../remote-storage/api';
import { applyTechnologyIntegration, compileTechnologyIntegration, previewTechnologyIntegration } from '../integration-designer/api';

afterEach(() => vi.restoreAllMocks());

describe('aggressive browser remote repository and integration transport', () => {
    it('proves connection -> binary export -> binary import preserves path, optimistic version and bytes', async () => {
        const payload = new Uint8Array([0, 1, 2, 127, 128, 254, 255]);
        const profileId = 'github/prod α';
        const remotePath = 'layers/model folder/a#b?.bin';
        const responses = [
            new Response(JSON.stringify({ profileId, connected: true, provider: 'GITHUB', message: 'ok' }), { status: 200, headers: { 'Content-Type': 'application/json' } }),
            new Response(JSON.stringify({ profileId, path: remotePath, version: 'etag:v2', size: payload.length }), { status: 200, headers: { 'Content-Type': 'application/json' } }),
            new Response(payload, { status: 200, headers: { 'Content-Type': 'application/octet-stream', 'Content-Disposition': "attachment; filename*=UTF-8''a%23b%3F.bin", 'x-hydra-remote-version': 'etag:v2' } }),
        ];
        const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation(async () => responses.shift() ?? new Response(null, { status: 500 }));

        await testRemoteStorageConnection(profileId);
        await saveRemoteLayerArtifact(profileId, remotePath, { fileName: 'a#b?.bin', content: payload, mimeType: 'application/octet-stream' }, { mode: 'UPDATE_ONLY', expectedVersion: 'etag:v1', commitMessage: 'aggressive round-trip' });
        const imported = await loadRemoteFileArtifact(profileId, remotePath);

        expect(fetchMock).toHaveBeenCalledTimes(3);
        const connectionUrl = String(fetchMock.mock.calls[0]?.[0]);
        expect(connectionUrl).toContain(`/profiles/${encodeURIComponent(profileId)}/test`);
        expect((fetchMock.mock.calls[0]?.[1] as RequestInit).method).toBe('POST');

        const exportUrl = new URL(String(fetchMock.mock.calls[1]?.[0]), 'http://hydra.local');
        expect(exportUrl.pathname).toBe('/api/remote-storage/layers/export');
        expect(exportUrl.searchParams.get('profileId')).toBe(profileId);
        expect(exportUrl.searchParams.get('remotePath')).toBe(remotePath);
        expect(exportUrl.searchParams.get('mode')).toBe('UPDATE_ONLY');
        expect(exportUrl.searchParams.get('expectedVersion')).toBe('etag:v1');
        expect(exportUrl.searchParams.get('commitMessage')).toBe('aggressive round-trip');
        const exportInit = fetchMock.mock.calls[1]?.[1] as RequestInit;
        expect(exportInit.body).toBeInstanceOf(FormData);
        expect(new Headers(exportInit.headers).get('X-Hydra-Request-Token')).toBe('aggressive-browser-token');
        const formFile = (exportInit.body as FormData).get('file');
        expect(formFile).toBeInstanceOf(File);
        expect((formFile as File).name).toBe('a#b?.bin');
        expect(new Uint8Array(await (formFile as File).arrayBuffer())).toEqual(payload);

        const importUrl = new URL(String(fetchMock.mock.calls[2]?.[0]), 'http://hydra.local');
        expect(importUrl.searchParams.get('profileId')).toBe(profileId);
        expect(importUrl.searchParams.get('remotePath')).toBe(remotePath);
        expect(imported.version).toBe('etag:v2');
        expect(imported.file.name).toBe('a#b?.bin');
        expect(imported.file.type).toBe('application/octet-stream');
        expect(imported.file.size).toBe(payload.byteLength);
        expect(new Uint8Array(await imported.file.arrayBuffer())).toEqual(payload);
    });

    it('proves preview -> compile -> apply keeps encoded semantic/profile identity and exact target directory', async () => {
        const semanticScopeId = 'grammar.people/v2';
        const profileId = 'integration profile/α';
        const targetDirectory = 'generated output/客户';
        const archive = new Uint8Array([80, 75, 3, 4, 10, 20, 30]);
        const preview = { plan: { targetDirectory, strategy: 'COPY_GENERATED_PROJECT', files: [] }, unchangedFiles: [], manualConflictFiles: [], generatedBytes: archive.length };
        const applied = { manifest: { id: 'manifest-1', integrationProfileId: profileId, targetTechnology: 'LANGIUM', files: [] }, writtenFiles: ['package.json'], deletedFiles: [], restoredFiles: [] };
        const responses = [
            new Response(JSON.stringify(preview), { status: 200, headers: { 'Content-Type': 'application/json' } }),
            new Response(archive, { status: 200, headers: { 'Content-Type': 'application/zip' } }),
            new Response(JSON.stringify(applied), { status: 200, headers: { 'Content-Type': 'application/json' } }),
        ];
        const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation(async () => responses.shift() ?? new Response(null, { status: 500 }));

        const previewResult = await previewTechnologyIntegration(semanticScopeId, profileId, targetDirectory);
        const compiled = await compileTechnologyIntegration(semanticScopeId, profileId);
        const applyResult = await applyTechnologyIntegration(semanticScopeId, profileId, targetDirectory);

        expect(previewResult.generatedBytes).toBe(archive.length);
        expect(new Uint8Array(await compiled.arrayBuffer())).toEqual(archive);
        expect(applyResult.writtenFiles).toEqual(['package.json']);

        const expectedBase = `/api/semantic-contexts/${encodeURIComponent(semanticScopeId)}/integrations/${encodeURIComponent(profileId)}/technology`;
        expect(String(fetchMock.mock.calls[0]?.[0])).toBe(`${expectedBase}/preview`);
        expect(String(fetchMock.mock.calls[1]?.[0])).toBe(`${expectedBase}/compile`);
        expect(String(fetchMock.mock.calls[2]?.[0])).toBe(`${expectedBase}/apply`);
        for (const index of [0, 2]) {
            const init = fetchMock.mock.calls[index]?.[1] as RequestInit;
            expect(init.method).toBe('POST');
            expect(JSON.parse(String(init.body))).toEqual({ targetDirectory });
        }
        expect((fetchMock.mock.calls[1]?.[1] as RequestInit).method).toBe('POST');
    });
});
