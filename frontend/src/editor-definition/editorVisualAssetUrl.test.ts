import { afterEach, describe, expect, it, vi } from 'vitest';
import { HYDRA_CORRELATION_HEADER } from '../api/backendFetch';
import { editorVisualAssetUrl, loadEditorVisualAssetResource } from './editorVisualAssetUrl';

vi.mock('../project-explorer/projectApi', () => ({
    projectFileVisualUrl: vi.fn((path: string) => `/api/projects/active/visual?path=${encodeURIComponent(path)}`)
}));

describe('editor visual asset URL', () => {
    afterEach(() => {
        vi.unstubAllGlobals();
    });

    it('routes canonical imported figures through the editor asset endpoint instead of the active project', () => {
        expect(editorVisualAssetUrl('hydra-assets/abc.svg')).toBe('/api/editor-assets?path=hydra-assets%2Fabc.svg');
        expect(editorVisualAssetUrl('external-source/icons/person.svg')).toContain('/api/projects/active/visual');
    });

    it('embeds a canonical visual figure as a data URL for portable standalone exports', async () => {
        const svg = '<svg xmlns="http://www.w3.org/2000/svg"><rect width="4" height="4"/></svg>';
        const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
            void input; void init;
            return new Response(svg, { status: 200, headers: { 'Content-Type': 'image/svg+xml' } });
        });
        vi.stubGlobal('fetch', fetchMock);

        const value = await loadEditorVisualAssetResource('hydra-assets/abc.svg');

        expect(fetchMock).toHaveBeenCalledTimes(1);
        expect(fetchMock).toHaveBeenCalledWith(
            '/api/editor-assets?path=hydra-assets%2Fabc.svg',
            expect.objectContaining({ headers: expect.any(Headers) })
        );
        const requestInit = fetchMock.mock.calls[0]?.[1];
        expect(requestInit?.headers).toBeInstanceOf(Headers);
        expect((requestInit?.headers as Headers).get(HYDRA_CORRELATION_HEADER)).toBeTruthy();
        expect(value).toMatch(/^data:image\/svg\+xml;base64,/);
        expect(atob(value.substring(value.indexOf(',') + 1))).toBe(svg);
    });
});
