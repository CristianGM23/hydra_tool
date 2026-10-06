import { afterEach, describe, expect, it, vi } from 'vitest';
import { loadCapabilityReadiness } from './api';

describe('capability readiness API', () => {
    afterEach(() => vi.unstubAllGlobals());
    it('loads metamodel and grammar capability inventories through explicit context routes', async () => {
        const fetchMock = vi.fn().mockImplementation(async (input: RequestInfo | URL) => {
            const grammar = String(input).includes('/grammar/');
            const contextId = grammar ? 'orders.dsl' : 'people space';
            const body = { contextId, contextName: grammar ? 'Orders' : 'People', contextType: grammar ? 'GRAMMAR' : 'METAMODEL', semanticContextId: contextId, score: 50, configuredLayers: 3, totalLayers: 7, layers: [], recommendedNextSteps: [] };
            return new Response(JSON.stringify(body), { status: 200, headers: { 'Content-Type': 'application/json' } });
        });
        vi.stubGlobal('fetch', fetchMock);
        await expect(loadCapabilityReadiness('people space')).resolves.toMatchObject({ score: 50, contextType: 'METAMODEL' });
        expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/hydra/capabilities/metamodel/people%20space');
        await expect(loadCapabilityReadiness('GRAMMAR', 'orders.dsl')).resolves.toMatchObject({ score: 50, contextType: 'GRAMMAR' });
        expect(fetchMock.mock.calls[1]?.[0]).toBe('/api/hydra/capabilities/grammar/orders.dsl');
    });
});
