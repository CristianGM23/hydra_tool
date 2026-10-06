import type { EventFlowModel, FlowDefinition } from './types';
export function findEventFlowBlockingError(model: EventFlowModel): string {
    if(model.id.trim() === '') return 'Event flow model ID is required.';
    if(model.flows.some(flow => flow.id.trim() === '' || flow.name.trim() === '')) return 'Every flow needs a stable ID and name.';
    const flowIds = model.flows.map(flow => flow.id);
    if(new Set(flowIds).size !== flowIds.length) return 'Flow IDs must be unique.';
    for(const flow of model.flows) {
        const error = validateFlow(flow);
        if(error !== '') return error;
    }
    return '';
}
export function validateFlow(flow: FlowDefinition): string {
    const nodeIds = flow.nodes.map(node => node.id);
    if(new Set(nodeIds).size !== nodeIds.length) return `Flow ${flow.name} contains duplicate node IDs.`;
    const starts = flow.nodes.filter(node => node.kind === 'START' || node.kind === 'SOURCE');
    if(starts.length !== 1) return `Flow ${flow.name} must contain exactly one Start or Source node.`;
    if(!flow.nodes.some(node => node.kind === 'SUCCESS_END' || node.kind === 'ERROR_END')) return `Flow ${flow.name} needs at least one terminal node.`;
    const known = new Set(nodeIds);
    if(flow.transitions.some(transition => !known.has(transition.sourceNodeId) || !known.has(transition.targetNodeId))) return `Flow ${flow.name} contains a transition with an unknown endpoint.`;
    for(const decision of flow.nodes.filter(node => node.kind === 'DECISION' || node.kind === 'ROUTE')) {
        const branches = flow.transitions.filter(transition => transition.sourceNodeId === decision.id).map(transition => transition.kind);
        if(!branches.includes('TRUE') || !branches.includes('FALSE')) return `Decision ${decision.name} needs TRUE and FALSE branches.`;
    }
    return '';
}
