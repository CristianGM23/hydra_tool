import { describe, expect, it } from 'vitest';
import { createDefaultFlow, createEmptyEventFlowDiagram, createEmptyEventFlowModel, createFlowNode, createFlowTransition, synchronizeEventFlowDiagram } from './eventFlowFactory';

describe('event flow factory', () => {
    it('creates a canonical document for the metamodel', () => { const model = createEmptyEventFlowModel('people', 'people-events'); expect(model.metamodelId).toBe('people'); expect(model.eventDefinitionModelId).toBe('people-events'); });
    it('creates an empty flow collection', () => { expect(createEmptyEventFlowModel('people', 'people-events').flows).toEqual([]); });
    it('creates a default flow with one start', () => { expect(createDefaultFlow().nodes.filter(node => node.kind === 'START')).toHaveLength(1); });
    it('creates a default flow with one success end', () => { expect(createDefaultFlow().nodes.filter(node => node.kind === 'SUCCESS_END')).toHaveLength(1); });
    it('starts without an implicit transition so the user defines the process path explicitly', () => { expect(createDefaultFlow().transitions).toEqual([]); });
    it('creates nodes with neutral retry and timeout policies', () => { const node = createFlowNode('MAP'); expect(node.retryPolicy.maxAttempts).toBe(0); expect(node.timeoutPolicy.timeoutMillis).toBe(0); });
    it('creates disabled-independent nodes as enabled', () => { expect(createFlowNode('FILTER').enabled).toBe(true); });
    it('creates stable transition identifiers', () => { expect(createFlowTransition('node:a', 'node:b').id).toMatch(/^transition:/); });
    it('creates a diagram position for each semantic node', () => { const model = createEmptyEventFlowModel('people', 'people-events'); model.flows = [createDefaultFlow()]; expect(createEmptyEventFlowDiagram(model).nodes).toHaveLength(2); });
    it('removes deleted nodes from the diagram', () => { const model = createEmptyEventFlowModel('people', 'people-events'); const flow = createDefaultFlow(); model.flows = [flow]; const diagram = createEmptyEventFlowDiagram(model); model.flows = [{ ...flow, nodes: [flow.nodes[0]], transitions: [] }]; expect(synchronizeEventFlowDiagram(model, diagram).nodes.map(node => node.nodeId)).toEqual([flow.nodes[0].id]); });
    it('adds missing semantic nodes to an existing diagram', () => { const model = createEmptyEventFlowModel('people', 'people-events'); model.flows = [createDefaultFlow()]; const diagram = { ...createEmptyEventFlowDiagram(model), nodes: [] }; expect(synchronizeEventFlowDiagram(model, diagram).nodes).toHaveLength(2); });
    it('keeps the event flow model reference synchronized', () => { const model = createEmptyEventFlowModel('people', 'people-events'); const diagram = createEmptyEventFlowDiagram(model); expect(synchronizeEventFlowDiagram({ ...model, id: 'people-next-flow-model' }, diagram).eventFlowModelId).toBe('people-next-flow-model'); });
});
