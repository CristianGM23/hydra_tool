import type { CompensationLink, EventFlowModel, FlowDefinition, FlowNode, FlowTransition, FlowVariable } from './types';

/** Texto tolerante: el backend omite las cadenas vacías, así que un valor ausente equivale a «». */
const str = (value: unknown): string => (typeof value === 'string' ? value : '');
const num = (value: unknown, fallback: number): number => (typeof value === 'number' && Number.isFinite(value) ? value : fallback);
const arr = <T>(value: unknown): T[] => (Array.isArray(value) ? (value as T[]) : []);
const rec = <T>(value: unknown): Record<string, T> => (value !== null && typeof value === 'object' && !Array.isArray(value) ? (value as Record<string, T>) : {});

/** Rellena los campos que el backend omite en un nodo para que el diseñador pueda leerlos siempre como texto. */
export function normalizeFlowNode(node: FlowNode): FlowNode {
    const retry = rec<unknown>(node.retryPolicy);
    const timeout = rec<unknown>(node.timeoutPolicy);
    return {
        ...node,
        id: str(node.id), name: str(node.name), referenceId: str(node.referenceId), eventTypeId: str(node.eventTypeId),
        conditionExpressionId: str(node.conditionExpressionId), configuration: rec<unknown>(node.configuration),
        retryPolicy: { maxAttempts: num(retry.maxAttempts, 0), delayMillis: num(retry.delayMillis, 0), backoffMultiplier: num(retry.backoffMultiplier, 1) },
        timeoutPolicy: { timeoutMillis: num(timeout.timeoutMillis, 0), timeoutTransitionId: str(timeout.timeoutTransitionId) },
        compensable: node.compensable === true, enabled: node.enabled !== false
    };
}

/** Rellena los campos que el backend omite en una transición. */
export function normalizeFlowTransition(transition: FlowTransition): FlowTransition {
    return { ...transition, id: str(transition.id), sourceNodeId: str(transition.sourceNodeId), targetNodeId: str(transition.targetNodeId), conditionExpressionId: str(transition.conditionExpressionId), order: num(transition.order, 0), label: str(transition.label) };
}

/** Normaliza un flujo completo (textos, listas y mapas ausentes). */
export function normalizeFlowDefinition(flow: FlowDefinition): FlowDefinition {
    return {
        ...flow,
        id: str(flow.id), name: str(flow.name), description: str(flow.description), enabled: flow.enabled !== false,
        triggerEventTypeIds: arr<string>(flow.triggerEventTypeIds),
        variables: arr<FlowVariable>(flow.variables).map(variable => ({ ...variable, id: str(variable.id), name: str(variable.name), type: str(variable.type) })),
        nodes: arr<FlowNode>(flow.nodes).map(normalizeFlowNode),
        transitions: arr<FlowTransition>(flow.transitions).map(normalizeFlowTransition),
        compensations: arr<CompensationLink>(flow.compensations).map(link => ({ ...link, id: str(link.id), sourceNodeId: str(link.sourceNodeId), compensationNodeId: str(link.compensationNodeId) })),
        annotations: rec<string>(flow.annotations)
    };
}

/** Normaliza el modelo de flujos devuelto por el backend, igual que el diseñador de eventos normaliza el suyo. */
export function normalizeEventFlowModel(model: EventFlowModel): EventFlowModel {
    return {
        ...model,
        id: str(model.id), metamodelId: str(model.metamodelId), eventDefinitionModelId: str(model.eventDefinitionModelId), expressionModelId: str(model.expressionModelId),
        flows: arr<FlowDefinition>(model.flows).map(normalizeFlowDefinition), annotations: rec<string>(model.annotations)
    };
}
