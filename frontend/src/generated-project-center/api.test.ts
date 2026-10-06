import { beforeEach, describe, expect, it, vi } from 'vitest';
import { executeGeneratedProject, loadGeneratedProjectProfiles } from './api';
vi.mock('../api/backendFetch', () => ({ backendFetch: vi.fn() }));
import { backendFetch } from '../api/backendFetch';
describe('generated project API', () => {
    beforeEach(() => vi.mocked(backendFetch).mockReset());
    it('loads server-owned profiles', async () => {
        vi.mocked(backendFetch).mockResolvedValue(new Response('[]', { status: 200 }));
        await expect(loadGeneratedProjectProfiles()).resolves.toEqual([]);
        expect(backendFetch).toHaveBeenCalledWith('/api/generated-projects/profiles');
    });
    it('sends selected lifecycle stages without arbitrary commands', async () => {
        vi.mocked(backendFetch).mockResolvedValue(new Response(JSON.stringify({ successful: true }), { status: 200 }));
        await executeGeneratedProject('sample', ['VALIDATE'], true, false);
        const options = vi.mocked(backendFetch).mock.calls[0]?.[1];
        expect(JSON.parse(String(options?.body))).toEqual(expect.objectContaining({
            projectId: 'sample', stages: ['VALIDATE'], dryRun: true
        }));
        expect(String(options?.body)).not.toContain('command');
    });
});
