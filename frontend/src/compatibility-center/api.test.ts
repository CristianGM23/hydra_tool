import { afterEach, describe, expect, it, vi } from 'vitest';
import { loadLatestCompatibilityMatrix, runNativeCompatibility } from './api';

describe('native compatibility API', () => {
    afterEach(() => vi.unstubAllGlobals());
    it('treats an absent latest matrix as an empty operational state', async () => {
        vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(null, { status: 204 })));
        await expect(loadLatestCompatibilityMatrix()).resolves.toBeNull();
    });
    it('sends only catalog keys and never arbitrary commands', async () => {
        const matrix = { generatedAt: '2026-07-31T08:00:00Z', overallStatus: 'PASSED', results: [] };
        const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify(matrix), { status: 200, headers: { 'Content-Type': 'application/json' } }));
        vi.stubGlobal('fetch', fetchMock);
        await runNativeCompatibility(['langium:configured']);
        expect(fetchMock).toHaveBeenCalledWith('/api/toolchain-compatibility/run', expect.objectContaining({ method: 'POST', body: JSON.stringify({ selectedKeys: ['langium:configured'] }) }));
    });
});
