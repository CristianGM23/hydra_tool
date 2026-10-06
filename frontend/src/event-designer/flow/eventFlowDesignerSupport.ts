import type { FlowDefinition, FlowDiagnostic, FlowNode, FlowNodeKind, FlowTransitionKind } from './types';

/**
 * Fusiona flujos por identificador estable.
 *
 * @param current contiene los flujos actuales.
 * @param imported contiene los flujos importados que prevalecen por identificador.
 * @returns colección fusionada sin identificadores duplicados.
 */
export function mergeFlowsById(current: readonly FlowDefinition[], imported: readonly FlowDefinition[]): FlowDefinition[] {
    const byId = new Map(current.map(item => [item.id, item]));
    for (const item of imported) byId.set(item.id, item);
    return [...byId.values()];
}

/**
 * Obtiene la severidad más alta asociada a un elemento del flujo.
 *
 * @param elementId identifica el elemento analizado.
 * @param diagnostics contiene los diagnósticos del flujo.
 * @returns severidad más alta o `undefined` cuando no hay diagnósticos.
 */
export function diagnosticSeverityFor(elementId: string, diagnostics: FlowDiagnostic[]): 'ERROR' | 'WARNING' | 'INFO' | undefined {
    const values = diagnostics.filter(item => item.elementId === elementId).map(item => item.severity);
    if (values.includes('ERROR')) return 'ERROR';
    if (values.includes('WARNING')) return 'WARNING';
    if (values.includes('INFO')) return 'INFO';
    return undefined;
}

/**
 * Calcula una disposición inicial legible para los nodos de un flujo.
 *
 * @param flow contiene la definición semántica del flujo.
 * @returns posiciones propuestas por identificador de nodo.
 */
export function layoutEventFlow(flow: FlowDefinition): Map<string, { x: number; y: number }> {
    const nodeIds = new Set(flow.nodes.map(node => node.id));
    const incoming = new Map<string, string[]>();
    const outgoing = new Map<string, string[]>();
    for (const node of flow.nodes) { incoming.set(node.id, []); outgoing.set(node.id, []); }
    for (const transition of flow.transitions) {
        if (!nodeIds.has(transition.sourceNodeId) || !nodeIds.has(transition.targetNodeId)) continue;
        outgoing.get(transition.sourceNodeId)!.push(transition.targetNodeId);
        incoming.get(transition.targetNodeId)!.push(transition.sourceNodeId);
    }
    const roots = flow.nodes.filter(node => node.kind === 'START' || node.kind === 'SOURCE' || (incoming.get(node.id)?.length ?? 0) === 0).map(node => node.id);
    const queue = [...roots];
    const layers = new Map<string, number>(roots.map(id => [id, 0]));
    const visits = new Map<string, number>();
    while (queue.length > 0) {
        const source = queue.shift()!;
        const sourceLayer = layers.get(source) ?? 0;
        for (const target of outgoing.get(source) ?? []) {
            layers.set(target, Math.max(layers.get(target) ?? 0, sourceLayer + 1));
            const count = (visits.get(target) ?? 0) + 1;
            visits.set(target, count);
            if (count <= flow.nodes.length) queue.push(target);
        }
    }
    let fallbackLayer = Math.max(0, ...layers.values()) + 1;
    for (const node of flow.nodes) if (!layers.has(node.id)) layers.set(node.id, fallbackLayer++);
    const grouped = new Map<number, FlowNode[]>();
    for (const node of flow.nodes) {
        const layer = layers.get(node.id) ?? 0;
        grouped.set(layer, [...(grouped.get(layer) ?? []), node]);
    }
    const result = new Map<string, { x: number; y: number }>();
    for (const [layer, nodes] of [...grouped.entries()].sort((left, right) => left[0] - right[0])) {
        nodes.sort((left, right) => left.name.localeCompare(right.name));
        nodes.forEach((node, index) => result.set(node.id, { x: 80 + layer * 290, y: 70 + index * 135 }));
    }
    return result;
}

/**
 * Clasifica un tipo de nodo para presentarlo de forma comprensible.
 *
 * @param kind contiene el tipo semántico del nodo.
 * @returns categoría de alto nivel.
 */
export function flowNodeCategory(kind: FlowNodeKind): string {
    if (['START', 'SUCCESS_END', 'ERROR_END'].includes(kind)) return 'Lifecycle';
    if (['SOURCE', 'EVENT_STEP', 'WAIT_FOR_EVENT', 'EMIT', 'MAP', 'FILTER', 'ROUTE'].includes(kind)) return 'Events and data';
    if (['MERGE', 'DEDUPLICATE', 'WINDOW', 'AGGREGATE', 'JOIN', 'SEQUENCE', 'PATTERN', 'ABSENCE'].includes(kind)) return 'CEP and state';
    if (['ACTION_STEP', 'OPERATION_STEP', 'COMMAND_STEP', 'MANUAL_STEP'].includes(kind)) return 'Execution';
    return 'Control and resilience';
}

/**
 * Clasifica un tipo de transición para presentarlo de forma comprensible.
 *
 * @param kind contiene el tipo semántico de transición.
 * @returns categoría de alto nivel.
 */
export function flowTransitionCategory(kind: FlowTransitionKind): string {
    if (['NEXT', 'ON_SUCCESS'].includes(kind)) return 'Normal execution';
    if (['TRUE', 'FALSE'].includes(kind)) return 'Decision branches';
    return 'Error and resilience';
}
