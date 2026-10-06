import { createDefaultFlow, createFlowId, createFlowNode, createFlowTransition } from './eventFlowFactory';
import type { CompensationLink, EventFlowModel, FlowDefinition, FlowNode, FlowNodeKind, FlowTransition, FlowVariable } from './types';

/** Pure immutable updates used by the EventFlow designer orchestrator. */
export function replaceFlow(model: EventFlowModel, flow: FlowDefinition): EventFlowModel {
    return { ...model, flows: model.flows.map(item => item.id === flow.id ? flow : item) };
}

export function renameFlow(model: EventFlowModel, previousId: string, nextId: string): EventFlowModel {
    if (previousId === nextId) return model;
    return { ...model, flows: model.flows.map(item => item.id === previousId ? { ...item, id: nextId } : item) };
}

export function replaceFlowNode(flow: FlowDefinition, node: FlowNode): FlowDefinition {
    return { ...flow, nodes: flow.nodes.map(item => item.id === node.id ? node : item) };
}

export function renameFlowNode(flow: FlowDefinition, previousId: string, nextId: string): FlowDefinition {
    if (previousId === nextId) return flow;
    return {
        ...flow,
        nodes: flow.nodes.map(node => node.id === previousId ? { ...node, id: nextId } : node),
        transitions: flow.transitions.map(transition => ({
            ...transition,
            sourceNodeId: transition.sourceNodeId === previousId ? nextId : transition.sourceNodeId,
            targetNodeId: transition.targetNodeId === previousId ? nextId : transition.targetNodeId
        })),
        compensations: flow.compensations.map(link => ({
            ...link,
            sourceNodeId: link.sourceNodeId === previousId ? nextId : link.sourceNodeId,
            compensationNodeId: link.compensationNodeId === previousId ? nextId : link.compensationNodeId
        }))
    };
}

export function replaceFlowTransition(flow: FlowDefinition, transition: FlowTransition): FlowDefinition {
    return { ...flow, transitions: flow.transitions.map(item => item.id === transition.id ? transition : item) };
}

export function renameFlowTransition(flow: FlowDefinition, previousId: string, nextId: string): FlowDefinition {
    if (previousId === nextId) return flow;
    return {
        ...flow,
        transitions: flow.transitions.map(item => item.id === previousId ? { ...item, id: nextId } : item),
        nodes: flow.nodes.map(node => node.timeoutPolicy.timeoutTransitionId === previousId
            ? { ...node, timeoutPolicy: { ...node.timeoutPolicy, timeoutTransitionId: nextId } }
            : node)
    };
}

export function appendFlow(model: EventFlowModel): { model: EventFlowModel; flow: FlowDefinition } {
    const flow = createDefaultFlow();
    return { model: { ...model, flows: [...model.flows, flow] }, flow };
}

export function removeFlow(model: EventFlowModel, flowId: string): EventFlowModel {
    return { ...model, flows: model.flows.filter(flow => flow.id !== flowId) };
}

export function appendFlowNode(flow: FlowDefinition, kind: FlowNodeKind): { flow: FlowDefinition; node: FlowNode } {
    const node = createFlowNode(kind);
    return { flow: { ...flow, nodes: [...flow.nodes, node] }, node };
}

export function removeFlowNode(flow: FlowDefinition, nodeId: string): FlowDefinition {
    return {
        ...flow,
        nodes: flow.nodes.filter(node => node.id !== nodeId),
        transitions: flow.transitions.filter(edge => edge.sourceNodeId !== nodeId && edge.targetNodeId !== nodeId),
        compensations: flow.compensations.filter(link => link.sourceNodeId !== nodeId && link.compensationNodeId !== nodeId)
    };
}

export function appendFlowTransition(flow: FlowDefinition, selectedNode: FlowNode | null): { flow: FlowDefinition; transition: FlowTransition } | null {
    if (flow.nodes.length < 2) return null;
    const sourceNode = selectedNode ?? flow.nodes[0];
    const targetNode = flow.nodes.find(node => node.id !== sourceNode.id) ?? flow.nodes[1];
    const kind = sourceNode.kind === 'DECISION' || sourceNode.kind === 'ROUTE' ? 'TRUE' : 'NEXT';
    const transition = createFlowTransition(sourceNode.id, targetNode.id, kind);
    return { flow: { ...flow, transitions: [...flow.transitions, transition] }, transition };
}

export function removeFlowTransition(flow: FlowDefinition, transitionId: string): FlowDefinition {
    return {
        ...flow,
        transitions: flow.transitions.filter(item => item.id !== transitionId),
        nodes: flow.nodes.map(node => node.timeoutPolicy.timeoutTransitionId === transitionId
            ? { ...node, timeoutPolicy: { ...node.timeoutPolicy, timeoutTransitionId: '' } }
            : node)
    };
}

export function appendFlowVariable(flow: FlowDefinition): FlowDefinition {
    const variable: FlowVariable = { id: createFlowId('variable'), name: 'value', type: 'STRING' };
    return { ...flow, variables: [...flow.variables, variable] };
}

export function appendFlowCompensation(flow: FlowDefinition): { flow: FlowDefinition; link: CompensationLink } | null {
    if (flow.nodes.length < 2) return null;
    const source = flow.nodes.find(node => node.compensable) ?? flow.nodes[0];
    const compensation = flow.nodes.find(node => node.kind === 'COMPENSATION_STEP' && node.id !== source.id)
        ?? flow.nodes.find(node => node.id !== source.id);
    if (compensation === undefined) return null;
    const link: CompensationLink = { id: createFlowId('compensation'), sourceNodeId: source.id, compensationNodeId: compensation.id };
    return { flow: { ...flow, compensations: [...flow.compensations, link] }, link };
}
