import { afterEach, describe, expect, it, vi } from 'vitest';
import { loadGamificationDiagram, saveGamificationDiagram } from './api';
import { createEmptyGamificationModels } from './gamificationFactory';
import { createEmptyGamificationDiagram } from './gamificationDiagram';

afterEach(() => vi.restoreAllMocks());

describe('gamification diagram API', () => {
    it('loads and saves the separate geometry with ETag', async () => {
        const models = createEmptyGamificationModels('people');
        const diagram = createEmptyGamificationDiagram(models.definitions, models.bindings);
        const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation(async () => new Response(JSON.stringify({ diagram, etag: 'next' }), {
            status: 200,
            headers: { 'Content-Type': 'application/json' }
        }));
        await loadGamificationDiagram('people');
        expect(fetchMock.mock.calls.at(-1)?.[0]).toBe('/api/canonical-metamodels/people/gamification/diagram');
        await saveGamificationDiagram(diagram, 'old');
        expect(fetchMock.mock.calls.at(-1)?.[0]).toBe('/api/canonical-metamodels/people/gamification/diagram');
        const init=fetchMock.mock.calls.at(-1)?.[1] as RequestInit;
        expect(init.method).toBe('PUT');
        expect(new Headers(init.headers).get('If-Match')).toBe('old');
    });
});
