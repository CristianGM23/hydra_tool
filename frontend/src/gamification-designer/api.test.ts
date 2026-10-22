import { afterEach, describe, expect, it, vi } from 'vitest';
import { createGamification, loadGamificationDiagram, replaceGamification, saveGamificationDiagram } from './api';
import { createEmptyGamificationModels, createRule } from './gamificationFactory';
import { createEmptyGamificationDiagram } from './gamificationDiagram';

afterEach(() => vi.restoreAllMocks());

describe('gamification catalog API: respuestas de create/replace', () => {
    /** Respuesta tal como la serializa el backend: omite las cadenas vacías de la regla. */
    const backendResponseWithoutEmptyStrings = () => {
        const models = createEmptyGamificationModels('people');
        const { conditionPath: _c, achievementId: _a, challengeId: _h, rewardId: _r, ...rule } = createRule('event:test', 'points:a');
        const definitions = { ...models.definitions, pointTypes: [{ id: 'points:a', name: 'A' }], levels: [{ id: 'level:a', name: 'L', pointTypeId: 'points:a' }] };
        return { models, body: { definitions, bindings: { ...models.bindings, rules: [rule] }, definitionEtag: 'd', bindingEtag: 'b' } };
    };
    const mockResponse = (body: unknown) => vi.spyOn(globalThis, 'fetch').mockImplementation(async () => new Response(JSON.stringify(body), { status: 200, headers: { 'Content-Type': 'application/json' } }));

    it('normaliza la respuesta de createGamification igual que la de carga', async () => {
        const { models, body } = backendResponseWithoutEmptyStrings();
        mockResponse(body);
        const result = await createGamification(models.definitions, models.bindings);
        const rule = result.bindings.rules[0];
        expect(rule.conditionPath).toBe('');
        expect(rule.rewardId).toBe('');
        expect(rule.achievementId).toBe('');
        expect(rule.challengeId).toBe('');
        expect(result.definitions.levels[0].rewardId).toBe('');
        expect(() => rule.conditionPath.trim()).not.toThrow();
    });

    it('normaliza la respuesta de replaceGamification igual que la de carga', async () => {
        const { models, body } = backendResponseWithoutEmptyStrings();
        mockResponse(body);
        const result = await replaceGamification(models.definitions, 'd', models.bindings, 'b');
        const rule = result.bindings.rules[0];
        expect(rule.conditionPath).toBe('');
        expect(rule.rewardId).toBe('');
        expect(result.definitions.pointTypes[0].description).toBe('');
        expect(result.checkReport.diagnostics).toEqual([]);
    });
});

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

    it('sends every numeric connection field for a newly created edge so the backend can deserialize it', async () => {
        const models = createEmptyGamificationModels('people');
        const base = createEmptyGamificationDiagram(models.definitions, models.bindings);
        const diagram = { ...base, edges: [{ edgeId: 'rule-points:rule:a', points: [], sourceHandle: 'right-source', targetHandle: 'left-target', label: '', note: '', routeStyle: 'ORTHOGONAL' as const, lineStyle: 'SOLID' as const, sourceTerminator: 'NONE' as const, targetTerminator: 'ARROW_CLOSED' as const }] };
        const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation(async () => new Response(JSON.stringify({ diagram, etag: 'next' }), { status: 200, headers: { 'Content-Type': 'application/json' } }));
        await saveGamificationDiagram(diagram, 'old');
        const sent = JSON.parse((fetchMock.mock.calls.at(-1)?.[1] as RequestInit).body as string) as { edges: Array<Record<string, unknown>> };
        const edge = sent.edges[0];
        for (const field of ['lineWidth', 'lineOpacity', 'lineBorderWidth', 'sourceTerminatorBorderWidth', 'sourceTerminatorSize', 'targetTerminatorBorderWidth', 'targetTerminatorSize']) {
            expect(typeof edge[field], field).toBe('number');
        }
        expect(edge.lineWidth).toBe(0);
        expect(edge.lineOpacity).toBe(1);
        expect(edge.targetTerminator).toBe('ARROW_CLOSED');
    });
});
