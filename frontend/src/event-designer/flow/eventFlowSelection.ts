import type { FlowDefinition } from './types';

/** Removes a multi-selection while preserving the canonical flow invariants. */
export function removeEventFlowSelection(flow: FlowDefinition, nodeIds: readonly string[], transitionIds: readonly string[]): FlowDefinition {
    const nodes = new Set(nodeIds);
    const transitions = new Set(transitionIds);
    for (const transition of flow.transitions) if (nodes.has(transition.sourceNodeId) || nodes.has(transition.targetNodeId)) transitions.add(transition.id);
    return {
        ...flow,
        nodes: flow.nodes.filter(node => !nodes.has(node.id)).map(node => transitions.has(node.timeoutPolicy.timeoutTransitionId) ? { ...node, timeoutPolicy: { ...node.timeoutPolicy, timeoutTransitionId: '' } } : node),
        transitions: flow.transitions.filter(transition => !transitions.has(transition.id)),
        compensations: flow.compensations.filter(link => !nodes.has(link.sourceNodeId) && !nodes.has(link.compensationNodeId))
    };
}
