import { describe, expect, it } from 'vitest';
import { createDefaultFlow, createEmptyEventFlowModel, createFlowTransition } from './eventFlowFactory';
import { buildEventFlowStructure } from './eventFlowStructureModel';

describe('event-flow structure tree', () => {
    it('shows nodes and explicit transitions for every flow', () => { const model=createEmptyEventFlowModel('people','people-events'); const flow=createDefaultFlow(); flow.transitions=[createFlowTransition(flow.nodes[0].id,flow.nodes[1].id)]; model.flows=[flow]; const json=JSON.stringify(buildEventFlowStructure(model)); expect(json).toContain('Nodes'); expect(json).toContain(flow.nodes[0].id); expect(json).toContain(flow.transitions[0].id); });
    it('shows variables and compensations as editable flow-owned elements', () => { const model=createEmptyEventFlowModel('people','people-events'); const flow=createDefaultFlow(); flow.variables=[{id:'variable:actor',name:'actor',type:'STRING'}]; flow.compensations=[{id:'comp:one',sourceNodeId:flow.nodes[0].id,compensationNodeId:flow.nodes[1].id}]; model.flows=[flow]; const json=JSON.stringify(buildEventFlowStructure(model)); expect(json).toContain('actor'); expect(json).toContain('COMPENSATION'); });
    it('retains disabled flow state in the tree badge', () => { const model=createEmptyEventFlowModel('people','people-events'); model.flows=[{...createDefaultFlow(),enabled:false}]; expect(JSON.stringify(buildEventFlowStructure(model))).toContain('DISABLED'); });
});
