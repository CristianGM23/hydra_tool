import { afterEach, describe, expect, it, vi } from 'vitest';
import { createEventFlows, loadEventFlows, replaceEventFlows, saveEventFlowDiagram } from './api';
import { createEmptyEventFlowDiagram, createEmptyEventFlowModel } from './eventFlowFactory';

afterEach(() => vi.restoreAllMocks());

/** Respuesta con la forma real del backend: el modelo viaja bajo `eventFlowModel`. */
function backendBody(model: ReturnType<typeof createEmptyEventFlowModel>): Response {
    return new Response(JSON.stringify({ eventFlowModel: model, etag: 'e1', checkReport: { eventFlowModelId: model.id, diagnostics: [] } }), { status: 200, headers: { 'Content-Type': 'application/json' } });
}

describe('event flow API contract', () => {
    it('sends every numeric connection field for a new transition edge when saving the diagram', async () => {
        const model = createEmptyEventFlowModel('people', 'people-events');
        const base = createEmptyEventFlowDiagram(model);
        const diagram = { ...base, edges: [{ transitionId: 'transition:one', points: [], sourceHandle: 'right-source', targetHandle: 'left-target', label: '', note: '', routeStyle: 'ORTHOGONAL' as const, lineStyle: 'SOLID' as const, sourceTerminator: 'NONE' as const, targetTerminator: 'ARROW_CLOSED' as const }] };
        const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation(async () => new Response(JSON.stringify({ diagram, etag: 'd1' }), { status: 200, headers: { 'Content-Type': 'application/json' } }));
        await saveEventFlowDiagram(diagram, '');
        const sent = JSON.parse((fetchMock.mock.calls.at(-1)?.[1] as RequestInit).body as string) as { edges: Array<Record<string, unknown>> };
        expect(typeof sent.edges[0].lineWidth).toBe('number');
        expect(typeof sent.edges[0].targetTerminatorSize).toBe('number');
    });

    it('maps the backend eventFlowModel key to the model the designer reads when loading', async () => {
        const model = createEmptyEventFlowModel('people', 'people-events');
        vi.spyOn(globalThis, 'fetch').mockResolvedValue(backendBody(model));
        const result = await loadEventFlows('people');
        expect(result.model.flows).toEqual([]);
        expect(result.model.id).toBe(model.id);
        expect(result.etag).toBe('e1');
        expect(result.checkReport.diagnostics).toEqual([]);
    });

    it('restores the empty strings the backend omits so a reloaded flow can be described', async () => {
        const model = createEmptyEventFlowModel('people', 'people-events');
        const wire = { ...model, flows: [{ id: 'f', name: 'F', enabled: true, nodes: [{ id: 'n', name: 'Start', kind: 'START', enabled: true }] }] };
        vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(JSON.stringify({ eventFlowModel: wire, etag: 'e1', checkReport: { diagnostics: [] } }), { status: 200, headers: { 'Content-Type': 'application/json' } }));
        const result = await loadEventFlows('people');
        expect(result.model.flows[0].nodes[0].eventTypeId).toBe('');
        expect(result.model.flows[0].nodes[0].retryPolicy.maxAttempts).toBe(0);
    });

    it('maps the backend response when creating and replacing flows', async () => {
        const model = createEmptyEventFlowModel('people', 'people-events');
        const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation(async () => backendBody(model));
        const created = await createEventFlows(model);
        expect(created.model.flows).toEqual([]);
        const replaced = await replaceEventFlows(model, 'e0');
        expect(replaced.model.id).toBe(model.id);
        const init = fetchMock.mock.calls.at(-1)?.[1] as RequestInit;
        expect(init.method).toBe('PUT');
        expect(new Headers(init.headers).get('If-Match')).toBe('e0');
    });
});
