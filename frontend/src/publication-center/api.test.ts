import { describe, expect, it, vi } from 'vitest';
import { publishDistribution } from './api';
vi.mock('../api/backendFetch', () => ({ backendFetch: vi.fn() }));
import { backendFetch } from '../api/backendFetch';
describe('publication API', () => {
    it('publishes metadata and artifact references but never signing secrets', async () => {
        vi.mocked(backendFetch).mockResolvedValue(new Response(JSON.stringify({ manifest: {} }), { status: 200 }));
        await publishDistribution({
            productId: 'sample', version: '1.0.0', channel: 'INTERNAL',
            signingMode: 'CHECKSUM_ONLY', distributionDirectory: 'sample', updateBaseUrl: '',
            artifacts: [{ platform: 'windows-x64', relativePath: 'editor.zip', mediaType: 'application/zip' }],
            metadata: {}
        });
        const body = String(vi.mocked(backendFetch).mock.calls[0]?.[1]?.body);
        expect(body).toContain('CHECKSUM_ONLY');
        expect(body.toLowerCase()).not.toContain('privatekey');
    });
});
