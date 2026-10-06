import { describe, expect, it } from 'vitest';
import { removeEventFlowSelection } from './eventFlowSelection';
import type { FlowDefinition } from './types';

const flow: FlowDefinition = {
    id:'flow', name:'Flow', description:'', enabled:true, triggerEventTypeIds:[], variables:[], annotations:{},
    nodes:[
        { id:'a', name:'A', kind:'START', referenceId:'', eventTypeId:'', conditionExpressionId:'', configuration:{}, retryPolicy:{ maxAttempts:0,delayMillis:0,backoffMultiplier:1 }, timeoutPolicy:{ timeoutMillis:0,timeoutTransitionId:'t2' }, compensable:false, enabled:true },
        { id:'b', name:'B', kind:'ACTION_STEP', referenceId:'', eventTypeId:'', conditionExpressionId:'', configuration:{}, retryPolicy:{ maxAttempts:0,delayMillis:0,backoffMultiplier:1 }, timeoutPolicy:{ timeoutMillis:0,timeoutTransitionId:'' }, compensable:true, enabled:true },
        { id:'c', name:'C', kind:'SUCCESS_END', referenceId:'', eventTypeId:'', conditionExpressionId:'', configuration:{}, retryPolicy:{ maxAttempts:0,delayMillis:0,backoffMultiplier:1 }, timeoutPolicy:{ timeoutMillis:0,timeoutTransitionId:'' }, compensable:false, enabled:true }
    ],
    transitions:[
        { id:'t1',sourceNodeId:'a',targetNodeId:'b',kind:'NEXT',conditionExpressionId:'',order:0,label:'' },
        { id:'t2',sourceNodeId:'b',targetNodeId:'c',kind:'NEXT',conditionExpressionId:'',order:1,label:'' }
    ],
    compensations:[{ id:'comp',sourceNodeId:'b',compensationNodeId:'c' }]
};

describe('event flow multi-selection deletion', () => {
    it('removes selected nodes, incident transitions and compensation links atomically', () => {
        const result=removeEventFlowSelection(flow,['b'],[]);
        expect(result.nodes.map(node => node.id)).toEqual(['a','c']);
        expect(result.transitions).toEqual([]);
        expect(result.compensations).toEqual([]);
        expect(result.nodes[0].timeoutPolicy.timeoutTransitionId).toBe('');
    });
});
