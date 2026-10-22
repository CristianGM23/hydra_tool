import { describe, expect, it } from 'vitest';
import { createFlowNode } from './eventFlowFactory';
import { normalizeEventFlowModel } from './eventFlowNormalization';
import { describeFlowNode } from './flowPresentation';
import type { EventFlowModel, FlowNode } from './types';

/** Nodo con la forma que devuelve el backend: sin las cadenas vacías. */
function backendNode(kind: FlowNode['kind']): FlowNode {
    const { eventTypeId: _e, referenceId: _r, conditionExpressionId: _c, ...rest } = createFlowNode(kind);
    return rest as unknown as FlowNode;
}

describe('flow node presentation with backend-omitted empty strings', () => {
    it('describes a START node that has no eventTypeId or referenceId', () => {
        expect(describeFlowNode(backendNode('START'))).toBe('Entry point of the flow.');
    });
    it('describes an action step without referenceId using the generic text', () => {
        expect(describeFlowNode(backendNode('ACTION_STEP'))).toBe('Runs a reusable canonical action.');
    });
    it('still describes nodes that do carry an event type', () => {
        expect(describeFlowNode({ ...createFlowNode('EMIT'), eventTypeId: 'core.model.saved' })).toMatch(/^Publishes /);
    });
});

describe('event flow model normalization', () => {
    it('restores omitted strings, policies and collections so the designer can always read them', () => {
        const raw = { schemaVersion: '1.0', id: 'm-flows', metamodelId: 'm', eventDefinitionModelId: 'm-events', flows: [{ id: 'f', name: 'F', enabled: true, nodes: [{ id: 'n1', name: 'Start', kind: 'START' }], transitions: [{ id: 't', sourceNodeId: 'n1', targetNodeId: 'n2', kind: 'NEXT' }] }] } as unknown as EventFlowModel;
        const model = normalizeEventFlowModel(raw);
        const node = model.flows[0].nodes[0];
        expect(model.expressionModelId).toBe('');
        expect(node.eventTypeId).toBe('');
        expect(node.referenceId).toBe('');
        expect(node.configuration).toEqual({});
        expect(node.retryPolicy).toEqual({ maxAttempts: 0, delayMillis: 0, backoffMultiplier: 1 });
        expect(node.timeoutPolicy).toEqual({ timeoutMillis: 0, timeoutTransitionId: '' });
        expect(node.enabled).toBe(true);
        expect(model.flows[0].transitions[0].label).toBe('');
        expect(model.flows[0].triggerEventTypeIds).toEqual([]);
        expect(model.flows[0].compensations).toEqual([]);
        expect(describeFlowNode(node)).toBe('Entry point of the flow.');
    });
});
