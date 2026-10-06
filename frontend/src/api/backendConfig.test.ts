import { afterEach, describe, expect, it, vi } from 'vitest';
import { resolveBackendRequest, resolveBackendUrl } from './backendConfig';

afterEach(() => { vi.unstubAllEnvs(); });

describe('backendConfig', () => {
    it('keeps same-origin api URLs unchanged by default', () => {
        vi.stubEnv('VITE_HYDRA_API_BASE_URL', '');
        expect(resolveBackendUrl('/api/projects/active/export')).toBe('/api/projects/active/export');
    });

    it('resolves direct browser URLs against the configured backend base', () => {
        vi.stubEnv('VITE_HYDRA_API_BASE_URL', 'https://hydra.example.test/backend/');
        expect(resolveBackendUrl('/api/projects/active/export')).toBe('https://hydra.example.test/backend/api/projects/active/export');
        expect(resolveBackendRequest('/api/editor-assets?path=a.svg')).toBe('https://hydra.example.test/backend/api/editor-assets?path=a.svg');
    });

    it('does not rewrite non-api or already absolute URLs', () => {
        vi.stubEnv('VITE_HYDRA_API_BASE_URL', 'https://hydra.example.test');
        expect(resolveBackendUrl('/assets/logo.svg')).toBe('/assets/logo.svg');
        expect(resolveBackendUrl('https://cdn.example.test/x.svg')).toBe('https://cdn.example.test/x.svg');
    });
});
