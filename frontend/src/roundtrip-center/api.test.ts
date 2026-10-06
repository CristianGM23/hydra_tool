import { afterEach, describe, expect, it, vi } from 'vitest';
import { applyAdvancedRoundTrip, prepareAdvancedRoundTrip, rollbackAdvancedRoundTrip } from './api';

describe('advanced round-trip API', () => {
    afterEach(() => vi.unstubAllGlobals());
    function response(value: unknown): Response { return new Response(JSON.stringify(value), { status: 200, headers: { 'Content-Type': 'application/json' } }); }

    it('previews explicit element decisions below an encoded profile route', async () => {
        const fetchMock = vi.fn().mockResolvedValue(response({ operationId: 'sync-1', actions: [], unresolved: [], canApply: true }));
        vi.stubGlobal('fetch', fetchMock);
        await prepareAdvancedRoundTrip('people model', 'langium/profile', 'clients/people', [{ conflictKey: 'A@a.ts', choice: 'MERGE_SAFE', rationale: 'Keep manual code' }]);
        expect(fetchMock.mock.calls[0][0]).toBe('/api/canonical-metamodels/people%20model/integrations/langium%2Fprofile/technology/sync/resolve');
        expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toMatchObject({ targetDirectory: 'clients/people', decisions: [{ choice: 'MERGE_SAFE' }] });
    });

    it('separates apply and rollback endpoints', async () => {
        const fetchMock = vi.fn().mockImplementation(() => Promise.resolve(response({ operationId: 'sync-1', rollback: false, affectedPaths: [], journalPath: '.hydra/journal.json' })));
        vi.stubGlobal('fetch', fetchMock);
        await applyAdvancedRoundTrip('people', 'profile', 'target', []);
        await rollbackAdvancedRoundTrip('people', 'profile', 'target', 'sync/1');
        expect(fetchMock.mock.calls[0][0]).toContain('/resolve/apply');
        expect(fetchMock.mock.calls[1][0]).toContain('/resolve/rollback/sync%2F1');
    });
});
