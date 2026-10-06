import { describe, expect, it } from 'vitest';
import { createDefaultFlow, createEmptyEventFlowModel, createFlowNode, createFlowTransition } from './eventFlowFactory';
import { findEventFlowBlockingError, validateFlow } from './eventFlowValidation';

describe('event flow validation', () => {
    it('accepts an empty model', () => { expect(findEventFlowBlockingError(createEmptyEventFlowModel('people', 'people-events'))).toBe(''); });
    it('accepts the default flow', () => { expect(validateFlow(createDefaultFlow())).toBe(''); });
    it('requires a model ID', () => { const model = createEmptyEventFlowModel('people', 'people-events'); model.id = ''; expect(findEventFlowBlockingError(model)).toContain('ID'); });
    it('requires unique flow IDs', () => { const model = createEmptyEventFlowModel('people', 'people-events'); const flow = createDefaultFlow(); model.flows = [flow, flow]; expect(findEventFlowBlockingError(model)).toContain('unique'); });
    it('requires exactly one start', () => { const flow = createDefaultFlow(); flow.nodes = flow.nodes.filter(node => node.kind !== 'START'); expect(validateFlow(flow)).toContain('exactly one'); });
    it('rejects two starts', () => { const flow = createDefaultFlow(); flow.nodes.push(createFlowNode('START')); expect(validateFlow(flow)).toContain('exactly one'); });
    it('requires a terminal node', () => { const flow = createDefaultFlow(); flow.nodes = flow.nodes.filter(node => node.kind !== 'SUCCESS_END'); flow.transitions = []; expect(validateFlow(flow)).toContain('terminal'); });
    it('does not treat emit as a semantic end', () => { const flow = createDefaultFlow(); const start = flow.nodes[0]; const emit = createFlowNode('EMIT'); flow.nodes = [start, emit]; flow.transitions = [createFlowTransition(start.id, emit.id)]; expect(validateFlow(flow)).toContain('terminal'); });
    it('rejects unknown transition endpoints', () => { const flow = createDefaultFlow(); flow.transitions=[createFlowTransition(flow.nodes[0].id,'node:unknown')]; expect(validateFlow(flow)).toContain('unknown endpoint'); });
    it('requires decision TRUE branch', () => { const flow = createDefaultFlow(); const decision = createFlowNode('DECISION'); const start = flow.nodes[0]; const end = flow.nodes[1]; flow.nodes = [start, decision, end]; flow.transitions = [createFlowTransition(start.id, decision.id), { ...createFlowTransition(decision.id, end.id), kind: 'FALSE' }]; expect(validateFlow(flow)).toContain('TRUE and FALSE'); });
    it('requires decision FALSE branch', () => { const flow = createDefaultFlow(); const decision = createFlowNode('ROUTE'); const start = flow.nodes[0]; const end = flow.nodes[1]; flow.nodes = [start, decision, end]; flow.transitions = [createFlowTransition(start.id, decision.id), { ...createFlowTransition(decision.id, end.id), kind: 'TRUE' }]; expect(validateFlow(flow)).toContain('TRUE and FALSE'); });
    it('accepts complete decision branches', () => { const flow = createDefaultFlow(); const decision = createFlowNode('DECISION'); const start = flow.nodes[0]; const success = flow.nodes[1]; const error = createFlowNode('ERROR_END'); flow.nodes = [start, decision, success, error]; flow.transitions = [createFlowTransition(start.id, decision.id), { ...createFlowTransition(decision.id, success.id), kind: 'TRUE' }, { ...createFlowTransition(decision.id, error.id), kind: 'FALSE' }]; expect(validateFlow(flow)).toBe(''); });
});
