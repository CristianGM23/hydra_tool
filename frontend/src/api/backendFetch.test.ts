import { afterEach, describe, expect, it, vi } from 'vitest';
import { BackendUnavailableError, HYDRA_CORRELATION_HEADER, backendFetch } from './backendFetch';

describe('backendFetch', () => {
    afterEach(() => { vi.restoreAllMocks(); });

    it('uses same-origin API requests and adds one correlation id', async () => {
        const response = new Response('{}', { status: 200 });
        const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(response);
        await expect(backendFetch('/api/system/status')).resolves.toBe(response);
        expect(fetchMock).toHaveBeenCalledOnce();
        const [url, init] = fetchMock.mock.calls[0];
        expect(url).toBe('/api/system/status');
        expect(new Headers(init?.headers).get(HYDRA_CORRELATION_HEADER)).toMatch(/.+/);
    });

    it('preserves a caller correlation id for retries and cross-service tracing', async () => {
        vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response('{}', { status: 200 }));
        await backendFetch('/api/system/status', { headers: { [HYDRA_CORRELATION_HEADER]: 'test-correlation-42' } });
        const init = vi.mocked(globalThis.fetch).mock.calls[0][1];
        expect(new Headers(init?.headers).get(HYDRA_CORRELATION_HEADER)).toBe('test-correlation-42');
    });

    it('converts network failures into an actionable backend error', async () => {
        vi.spyOn(globalThis, 'fetch').mockRejectedValue(new TypeError('Failed to fetch'));
        await expect(backendFetch('/api/projects/session')).rejects.toEqual(expect.objectContaining({
            name: 'BackendUnavailableError', message: expect.stringContaining('same-origin /api'),
        }));
        await expect(backendFetch('/api/projects/session')).rejects.toBeInstanceOf(BackendUnavailableError);
    });
});
