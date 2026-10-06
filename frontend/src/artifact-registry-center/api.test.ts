import { describe, expect, it, vi } from 'vitest';
import { scanArtifacts } from './api';
vi.mock('../api/backendFetch', () => ({ backendFetch: vi.fn() }));
import { backendFetch } from '../api/backendFetch';
describe('artifact registry API', () => {
    it('requests a bounded server-side scan', async () => {
        vi.mocked(backendFetch).mockResolvedValue(new Response(JSON.stringify({ artifacts: [] }), { status: 200 }));
        await scanArtifacts();
        expect(backendFetch).toHaveBeenCalledWith('/api/artifact-registry/scan', { method: 'POST' });
    });
});
