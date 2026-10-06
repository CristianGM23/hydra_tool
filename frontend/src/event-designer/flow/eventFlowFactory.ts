import type { EventFlowDiagramDocument, EventFlowModel, FlowDefinition, FlowNode, FlowNodeKind, FlowTransition, FlowTransitionKind } from './types';
let sequence = 0;
export function createFlowId(prefix: string): string { sequence += 1; return `${prefix}:${Date.now().toString(36)}-${sequence.toString(36)}`; }
export function createEmptyEventFlowModel(metamodelId: string, eventDefinitionModelId: string): EventFlowModel {
    return { schemaVersion: '1.0', id: `${metamodelId}-event-flows`, metamodelId, eventDefinitionModelId, expressionModelId: '', flows: [], annotations: {} };
}
export function createDefaultFlow(): FlowDefinition {
    const start = createFlowNode('START');
    const end = createFlowNode('SUCCESS_END');
    return {
        id: createFlowId('flow'), name: 'New event flow', description: '', enabled: true, triggerEventTypeIds: [], variables: [],
        nodes: [start, end], transitions: [], compensations: [], annotations: {}
    };
}
export function createFlowNode(kind: FlowNodeKind): FlowNode {
    return {
        id: createFlowId('node'), name: humanize(kind), kind, referenceId: '', eventTypeId: '', conditionExpressionId: '', configuration: {},
        retryPolicy: { maxAttempts: 0, delayMillis: 0, backoffMultiplier: 1 }, timeoutPolicy: { timeoutMillis: 0, timeoutTransitionId: '' },
        compensable: false, enabled: true
    };
}
export function createFlowTransition(sourceNodeId: string, targetNodeId: string, kind: FlowTransitionKind = 'NEXT'): FlowTransition {
    return { id: createFlowId('transition'), sourceNodeId, targetNodeId, kind, conditionExpressionId: '', order: 0, label: '' };
}
export function createEmptyEventFlowDiagram(model: EventFlowModel): EventFlowDiagramDocument {
    const nodes = model.flows.flatMap((flow, flowIndex) => flow.nodes.map((node, nodeIndex) => ({
        nodeId: node.id, x: 80 + nodeIndex * 220, y: 80 + flowIndex * 220, width: 180, height: 72, collapsed: false
    })));
    return { schemaVersion: '1.0', id: `${model.metamodelId}-event-flow-diagram`, metamodelId: model.metamodelId, eventFlowModelId: model.id, nodes, edges: [] };
}
export function synchronizeEventFlowDiagram(model: EventFlowModel, diagram: EventFlowDiagramDocument): EventFlowDiagramDocument {
    const nodeIds = new Set(model.flows.flatMap(flow => flow.nodes.map(node => node.id)));
    const transitionIds = new Set(model.flows.flatMap(flow => flow.transitions.map(transition => transition.id)));
    const existingNodeIds = new Set(diagram.nodes.map(node => node.nodeId));
    const missing = [...nodeIds].filter(id => !existingNodeIds.has(id)).map((id, index) => ({
        nodeId: id, x: 80 + (index % 4) * 220, y: 80 + Math.floor(index / 4) * 150, width: 180, height: 72, collapsed: false
    }));
    return {
        ...diagram,
        eventFlowModelId: model.id,
        nodes: [...diagram.nodes.filter(node => nodeIds.has(node.nodeId)), ...missing],
        edges: [
            ...diagram.edges.filter(edge => transitionIds.has(edge.transitionId)),
            ...model.flows.flatMap(flow => flow.transitions).filter(transition => !diagram.edges.some(edge => edge.transitionId === transition.id)).map(transition => ({ transitionId: transition.id, points: [], sourceHandle: 'right-source', targetHandle: 'left-target', label: '', note: '', routeStyle: 'ORTHOGONAL' as const, lineStyle: 'SOLID' as const, sourceTerminator: 'NONE' as const, targetTerminator: 'ARROW_CLOSED' as const }))
        ]
    };
}
function humanize(value: string): string { return value.toLowerCase().split('_').map(part => `${part.charAt(0).toUpperCase()}${part.slice(1)}`).join(' '); }
