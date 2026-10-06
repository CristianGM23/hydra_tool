import { describe, expect, it, vi } from 'vitest';
import { previewRepresentableImport } from './api';
vi.mock('../api/backendFetch', () => ({ backendFetch: vi.fn() }));
import { backendFetch } from '../api/backendFetch';
describe('representable import API', () => {
    it('preserves explicit external change candidates', async () => {
        vi.mocked(backendFetch).mockResolvedValue(new Response(JSON.stringify({ applicable: true }), { status: 200 }));
        await previewRepresentableImport('ECORE', 'shop', 'SAFE', [{
            externalId: 'c1', changeKind: 'ADD', nativeKind: 'EClass',
            qualifiedName: 'shop.Customer', properties: {}
        }]);
        const body = JSON.parse(String(vi.mocked(backendFetch).mock.calls[0]?.[1]?.body));
        expect(body).toMatchObject({ technology: 'ECORE', metamodelId: 'shop', strategy: 'SAFE' });
        expect(body.changes).toHaveLength(1);
    });
});
