import { createFlowId, createFlowNode, createFlowTransition } from '../event-designer/flow/eventFlowFactory';
import type { EventFlowModel, FlowDefinition, FlowNode, FlowNodeKind, FlowTransition } from '../event-designer/flow/types';
import type { EventTypeDefinition } from '../event-designer/types';
import type { LayerExportArtifact, LayerImportAnalysis, LayerInterchangeFormat } from './layerInterchangeTypes';
import { makeLayerInterchangeEnvelope, unwrapLayerInterchangeEnvelope } from './layerInterchangeEnvelope';
import { makeBlocklyEnvelope, parseBlocklyLayerEnvelope, serializeBlocklyLayerJavaScript, serializeBlocklyLayerJson } from '../blockly/blocklyInterchange';
import { sanitizeIdentifier } from './interchangeSupport';

export type EventFlowInterchangeFormat = 'HYDRA_JSON' | 'NODE_RED_JSON' | 'SIDDHI' | 'BLOCKLY_JSON' | 'BLOCKLY_JAVASCRIPT';
export const EVENT_FLOW_INTERCHANGE_FORMATS: readonly LayerInterchangeFormat<EventFlowInterchangeFormat>[] = [
    { id: 'HYDRA_JSON', label: 'HYDRA EventFlow JSON', description: 'Lossless HYDRA EventFlow document.', extensions: ['.eventflow.json', '.json'], mimeType: 'application/json' },
    { id: 'NODE_RED_JSON', label: 'Node-RED flow JSON', description: 'Imports/exports Node-RED tabs, nodes and wires. HYDRA custom properties preserve node semantics during round trips.', extensions: ['.nodered.json', '.json'], mimeType: 'application/json' },
    { id: 'SIDDHI', label: 'Siddhi application', description: 'Maps common Siddhi from/filter/select/insert queries to SOURCE/FILTER/MAP/EMIT EventFlow nodes and preserves full HYDRA flows in metadata comments.', extensions: ['.siddhi'], mimeType: 'text/plain' },
    { id: 'BLOCKLY_JSON', label: 'Blockly EventFlow layer (JSON)', description: 'Exports the complete EventFlow model as an independent Blockly-compatible HYDRA layer so an existing block editor can receive flows without replacing its metamodel or block catalogue.', extensions: ['.eventflow.blockly.json', '.blockly.json'], mimeType: 'application/json' },
    { id: 'BLOCKLY_JAVASCRIPT', label: 'Blockly EventFlow layer (JavaScript · recommended for injection)', description: 'Recommended for direct Blockly injection: exports an independent EventFlow registration module. HYDRA parses compatible imported JavaScript declaratively and never executes it.', extensions: ['.eventflow.blockly.js', '.blockly.js', '.js'], mimeType: 'text/javascript' }
];

export interface ImportedEventFlowLayer { model: EventFlowModel; warnings: string[]; format: EventFlowInterchangeFormat; }

export function analyzeEventFlowImport(content: string, format: EventFlowInterchangeFormat, current: EventFlowModel, eventTypes: readonly EventTypeDefinition[]): LayerImportAnalysis {
    const imported = importEventFlowLayer(content, format, current, eventTypes);
    const invalidFlows = imported.model.flows.filter(flow => flow.nodes.length === 0 || !flow.nodes.some(node => node.kind === 'START') || !flow.nodes.some(node => node.kind === 'SUCCESS_END' || node.kind === 'ERROR_END'));
    const danglingTransitions = imported.model.flows.flatMap(flow => {
        const nodeIds = new Set(flow.nodes.map(node => node.id));
        return flow.transitions.filter(transition => !nodeIds.has(transition.sourceNodeId) || !nodeIds.has(transition.targetNodeId));
    });
    const unresolvedEventRefs = imported.model.flows.reduce((sum, flow) => sum + flow.nodes.filter(node => ['SOURCE','EMIT','EVENT_STEP','WAIT_FOR_EVENT'].includes(node.kind) && node.eventTypeId === '').length, 0);
    const nodeCount = imported.model.flows.reduce((sum, flow) => sum + flow.nodes.length, 0);
    const transitionCount = imported.model.flows.reduce((sum, flow) => sum + flow.transitions.length, 0);
    return {
        summary: imported.model.flows.length === 0 ? 'No EventFlow definition can be imported from this source.' : `${imported.model.flows.length} flow(s), ${nodeCount} node(s) and ${transitionCount} connection(s) identified.`,
        canImport: imported.model.flows.length > 0,
        metrics: [
            { id: 'flows', label: 'Flows identified', value: imported.model.flows.length, tone: imported.model.flows.length > 0 ? 'GOOD' : 'ERROR' },
            { id: 'nodes', label: 'Nodes identified', value: nodeCount, tone: 'NEUTRAL' },
            { id: 'connections', label: 'Connections identified', value: transitionCount, tone: 'NEUTRAL' },
            { id: 'invalid-flows', label: 'Erroneous flows', value: invalidFlows.length, tone: invalidFlows.length > 0 ? 'ERROR' : 'GOOD' },
            { id: 'broken-connections', label: 'Broken connections', value: danglingTransitions.length, tone: danglingTransitions.length > 0 ? 'ERROR' : 'GOOD' },
            { id: 'event-refs', label: 'Unresolved event references', value: unresolvedEventRefs, tone: unresolvedEventRefs > 0 ? 'WARNING' : 'GOOD' },
            { id: 'warnings', label: 'Warnings', value: imported.warnings.length, tone: imported.warnings.length > 0 ? 'WARNING' : 'GOOD' }
        ],
        diagnostics: [
            ...invalidFlows.map(flow => ({ severity: 'ERROR' as const, category: 'INVALID_FLOW', itemId: flow.id, message: `${flow.name}: the imported flow does not contain a complete start/end structure.` })),
            ...danglingTransitions.map(transition => ({ severity: 'ERROR' as const, category: 'BROKEN_CONNECTION', itemId: transition.id, message: `Transition ${transition.id}: source or target node does not exist in the imported flow.` })),
            ...imported.warnings.map(message => ({ severity: 'WARNING' as const, category: 'IMPORT_WARNING', message }))
        ],
        warnings: imported.warnings
    };
}

export function importEventFlowLayer(content: string, format: EventFlowInterchangeFormat, current: EventFlowModel, eventTypes: readonly EventTypeDefinition[]): ImportedEventFlowLayer {
    if (format === 'BLOCKLY_JSON' || format === 'BLOCKLY_JAVASCRIPT') {
        const envelope = parseBlocklyLayerEnvelope<EventFlowModel>(content);
        if (envelope === null || envelope.layer !== 'EVENT_FLOW') return { model: { ...current, flows: [], annotations: { ...current.annotations, 'hydra.interchange.format': format } }, warnings: ['This Blockly/JavaScript file does not contain a HYDRA EVENT_FLOW layer. Imported JavaScript is never executed.'], format };
        return { model: { ...structuredClone(envelope.payload), id: current.id, metamodelId: current.metamodelId, eventDefinitionModelId: current.eventDefinitionModelId, expressionModelId: current.expressionModelId, annotations: { ...envelope.payload.annotations, 'hydra.interchange.format': format } }, warnings: envelope.metamodelId !== current.metamodelId ? [`The layer was authored for metamodel ${envelope.metamodelId}; review event and expression references.`] : [], format };
    }
    if (format === 'HYDRA_JSON') {
        const envelope = unwrapLayerInterchangeEnvelope<EventFlowModel>(content, 'EVENT_FLOW', current.metamodelId);
        const parsed = envelope.payload;
        return { model: { ...parsed, id: current.id, metamodelId: current.metamodelId, eventDefinitionModelId: current.eventDefinitionModelId, annotations: { ...(parsed.annotations ?? {}), 'hydra.interchange.format': 'HYDRA_JSON' } }, warnings: envelope.warnings, format };
    }
    return format === 'NODE_RED_JSON' ? importNodeRed(content, current, eventTypes) : importSiddhi(content, current, eventTypes);
}

interface NodeRedNode { id: string; type: string; z?: string; name?: string; wires?: unknown; [key: string]: unknown; }

function importNodeRed(content: string, current: EventFlowModel, eventTypes: readonly EventTypeDefinition[]): ImportedEventFlowLayer {
    const parsed = JSON.parse(content) as NodeRedNode[];
    if (!Array.isArray(parsed)) throw new Error('Node-RED import expects a JSON array of nodes.');
    const warnings: string[] = [];
    const tabs = parsed.filter(node => node.type === 'tab');
    const flows: FlowDefinition[] = [];
    for (const tab of tabs.length === 0 ? [{ id: 'default', type: 'tab', name: 'Imported Node-RED flow' } as NodeRedNode] : tabs) {
        const embedded = tab.hydraFlow;
        if (embedded !== undefined && embedded !== null && typeof embedded === 'object') {
            const flow = structuredClone(embedded) as FlowDefinition;
            flows.push({ ...flow, annotations: { ...(flow.annotations ?? {}), 'hydra.import.format': 'NODE_RED_JSON', 'hydra.import.sourceTabId': tab.id } });
            continue;
        }
        const sourceNodes = parsed.filter(node => node.type !== 'tab' && (tabs.length === 0 || node.z === tab.id));
        const idMap = new Map<string, string>();
        const nodes = sourceNodes.map(node => {
            const kind = nodeRedKind(node);
            const converted = createFlowNode(kind);
            idMap.set(node.id, converted.id);
            const eventTypeId = resolveEventType(String(node.eventTypeId ?? node.topic ?? ''), eventTypes);
            if ((kind === 'SOURCE' || kind === 'EMIT' || kind === 'EVENT_STEP') && eventTypeId === '' && String(node.topic ?? '') !== '') warnings.push(`Node-RED node ${node.name || node.id}: event/topic ${String(node.topic)} was not found in the current event catalog.`);
            return { ...converted, name: node.name || humanizeNodeRed(node.type), eventTypeId, referenceId: String(node.hydraReferenceId ?? ''), conditionExpressionId: String(node.hydraConditionExpressionId ?? ''), configuration: { nodeRedType: node.type, nodeRed: compactNodeRedConfiguration(node), ...(isObject(node.hydraConfiguration) ? node.hydraConfiguration : {}) }, enabled: node.d === true ? false : true };
        });
        const transitions: FlowTransition[] = [];
        for (const source of sourceNodes) {
            const sourceId = idMap.get(source.id); if (sourceId === undefined) continue;
            const wireGroups = Array.isArray(source.wires) ? source.wires : [];
            for (const group of wireGroups) if (Array.isArray(group)) for (const target of group) {
                const targetId = idMap.get(String(target));
                if (targetId !== undefined) transitions.push(createFlowTransition(sourceId, targetId, 'NEXT'));
            }
        }
        ensureStartAndEnd(nodes, transitions);
        const sourceEvents = nodes.filter(node => node.kind === 'SOURCE' && node.eventTypeId !== '').map(node => node.eventTypeId);
        flows.push({ id: createFlowId('flow'), name: tab.name || 'Imported Node-RED flow', description: `Imported from Node-RED tab ${tab.id}.`, enabled: true, triggerEventTypeIds: [...new Set(sourceEvents)], variables: [], nodes, transitions, compensations: [], annotations: { 'hydra.import.format': 'NODE_RED_JSON', 'hydra.import.sourceTabId': tab.id } });
    }
    if (flows.length === 0) warnings.push('No Node-RED tabs or executable nodes were recognized.');
    return { model: { ...current, flows, annotations: { ...current.annotations, 'hydra.interchange.format': 'NODE_RED_JSON' } }, warnings, format: 'NODE_RED_JSON' };
}

function importSiddhi(content: string, current: EventFlowModel, eventTypes: readonly EventTypeDefinition[]): ImportedEventFlowLayer {
    const warnings: string[] = [];
    const embedded = [...content.matchAll(/^\s*--\s*@hydra-flow-json\s+([^\s]+)\s*$/gmi)];
    if (embedded.length > 0) {
        const flows: FlowDefinition[] = [];
        for (const match of embedded) {
            try { const flow = JSON.parse(decodeURIComponent(match[1])) as FlowDefinition; flows.push({ ...flow, annotations: { ...(flow.annotations ?? {}), 'hydra.import.format': 'SIDDHI' } }); }
            catch { warnings.push('A HYDRA Siddhi metadata block could not be decoded; the textual query fallback will be used for that content.'); }
        }
        if (flows.length > 0) return { model: { ...current, flows, annotations: { ...current.annotations, 'hydra.interchange.format': 'SIDDHI' } }, warnings, format: 'SIDDHI' };
    }
    const flows: FlowDefinition[] = [];
    const pattern = /from\s+([A-Za-z_]\w*)\s*(?:\[([\s\S]*?)\])?\s+select\s+([\s\S]*?)\s+insert\s+into\s+([A-Za-z_]\w*)\s*;/gi;
    let match: RegExpExecArray | null;
    while ((match = pattern.exec(content)) !== null) {
        const start = createFlowNode('START');
        const source: FlowNode = { ...createFlowNode('SOURCE'), name: `Source ${match[1]}`, eventTypeId: resolveEventType(match[1], eventTypes), configuration: { siddhiStream: match[1] } };
        const nodeList: FlowNode[] = [start, source];
        const transitions: FlowTransition[] = [createFlowTransition(start.id, source.id)];
        let previous: FlowNode = source;
        if ((match[2] ?? '').trim() !== '') { const filter: FlowNode = { ...createFlowNode('FILTER'), name: 'Siddhi filter', configuration: { siddhiCondition: match[2].trim() } }; nodeList.push(filter); transitions.push(createFlowTransition(previous.id, filter.id)); previous = filter; }
        const select = match[3].trim();
        if (select !== '*' && select !== '') { const map: FlowNode = { ...createFlowNode('MAP'), name: 'Siddhi select', configuration: { siddhiSelect: select } }; nodeList.push(map); transitions.push(createFlowTransition(previous.id, map.id)); previous = map; }
        const emit: FlowNode = { ...createFlowNode('EMIT'), name: `Emit ${match[4]}`, eventTypeId: resolveEventType(match[4], eventTypes), configuration: { siddhiStream: match[4] } };
        const end = createFlowNode('SUCCESS_END');
        nodeList.push(emit, end); transitions.push(createFlowTransition(previous.id, emit.id), createFlowTransition(emit.id, end.id));
        if (source.eventTypeId === '') warnings.push(`Siddhi source stream ${match[1]} was not matched to a current event type.`);
        if (emit.eventTypeId === '') warnings.push(`Siddhi output stream ${match[4]} was not matched to a current event type.`);
        flows.push({ id: createFlowId('flow'), name: `Siddhi ${match[1]} to ${match[4]}`, description: 'Imported from a Siddhi query.', enabled: true, triggerEventTypeIds: source.eventTypeId === '' ? [] : [source.eventTypeId], variables: [], nodes: nodeList, transitions, compensations: [], annotations: { 'hydra.import.format': 'SIDDHI' } });
    }
    if (flows.length === 0) warnings.push('No Siddhi from … select … insert into … query was recognized.');
    return { model: { ...current, flows, annotations: { ...current.annotations, 'hydra.interchange.format': 'SIDDHI' } }, warnings, format: 'SIDDHI' };
}

export function exportEventFlowLayer(format: EventFlowInterchangeFormat, model: EventFlowModel, eventTypes: readonly EventTypeDefinition[]): LayerExportArtifact {
    if (format === 'BLOCKLY_JSON' || format === 'BLOCKLY_JAVASCRIPT') {
        const envelope = makeBlocklyEnvelope('EVENT_FLOW', model.metamodelId, model);
        return format === 'BLOCKLY_JSON' ? { fileName: `${model.metamodelId}.eventflow.blockly.json`, mimeType: 'application/json', content: serializeBlocklyLayerJson(envelope) } : { fileName: `${model.metamodelId}.eventflow.blockly.js`, mimeType: 'text/javascript', content: serializeBlocklyLayerJavaScript(envelope) };
    }
    if (format === 'HYDRA_JSON') return { fileName: `${model.metamodelId}.eventflow.json`, mimeType: 'application/json', content: JSON.stringify(makeLayerInterchangeEnvelope('EVENT_FLOW', model.metamodelId, model, [model.eventDefinitionModelId, model.expressionModelId].filter(item => item !== '')), null, 2) };
    if (format === 'NODE_RED_JSON') {
        const result: NodeRedNode[] = [];
        for (const [flowIndex, flow] of model.flows.entries()) {
            const tabId = `hydra-tab-${flowIndex + 1}-${sanitizeIdentifier(flow.id, 'flow')}`;
            result.push({ id: tabId, type: 'tab', label: flow.name, name: flow.name, disabled: !flow.enabled, hydraFlow: flow });
            const transitionsBySource = new Map<string, FlowTransition[]>();
            for (const transition of flow.transitions) transitionsBySource.set(transition.sourceNodeId, [...(transitionsBySource.get(transition.sourceNodeId) ?? []), transition]);
            for (const [nodeIndex, node] of flow.nodes.entries()) {
                result.push({ id: node.id, type: nodeRedType(node.kind), z: tabId, name: node.name, topic: eventName(node.eventTypeId, eventTypes), eventTypeId: node.eventTypeId, hydraKind: node.kind, hydraReferenceId: node.referenceId, hydraConditionExpressionId: node.conditionExpressionId, hydraConfiguration: node.configuration, d: !node.enabled, x: 160 + (nodeIndex % 5) * 190, y: 100 + Math.floor(nodeIndex / 5) * 120, wires: [(transitionsBySource.get(node.id) ?? []).map(transition => transition.targetNodeId)] });
            }
        }
        return { fileName: `${model.metamodelId}.nodered.json`, mimeType: 'application/json', content: JSON.stringify(result, null, 2) };
    }
    const blocks = model.flows.map(flow => {
        const source = flow.nodes.find(node => node.kind === 'SOURCE' || node.kind === 'START');
        const emit = flow.nodes.find(node => node.kind === 'EMIT');
        const filter = flow.nodes.find(node => node.kind === 'FILTER');
        const map = flow.nodes.find(node => node.kind === 'MAP');
        const input = source?.eventTypeId ? eventName(source.eventTypeId, eventTypes) : flow.triggerEventTypeIds[0] ? eventName(flow.triggerEventTypeIds[0], eventTypes) : 'HydraInput';
        const output = emit?.eventTypeId ? eventName(emit.eventTypeId, eventTypes) : 'HydraOutput';
        const condition = typeof filter?.configuration.siddhiCondition === 'string' ? `[${filter.configuration.siddhiCondition}]` : '';
        const select = typeof map?.configuration.siddhiSelect === 'string' ? map.configuration.siddhiSelect : '*';
        return [`-- HYDRA EventFlow: ${flow.name}`, `-- @hydra-flow-json ${encodeURIComponent(JSON.stringify(flow))}`, `from ${sanitizeIdentifier(input, 'HydraInput')}${condition}`, `select ${select}`, `insert into ${sanitizeIdentifier(output, 'HydraOutput')};`].join('\n');
    });
    return { fileName: `${model.metamodelId}.siddhi`, mimeType: 'text/plain', content: `${blocks.join('\n\n')}\n` };
}

function ensureStartAndEnd(nodes: FlowNode[], transitions: FlowTransition[]): void {
    if (!nodes.some(node => node.kind === 'START')) {
        const start = createFlowNode('START'); const first = nodes[0]; nodes.unshift(start); if (first !== undefined) transitions.unshift(createFlowTransition(start.id, first.id));
    }
    if (!nodes.some(node => node.kind === 'SUCCESS_END' || node.kind === 'ERROR_END')) {
        const end = createFlowNode('SUCCESS_END');
        const outgoing = new Set(transitions.map(item => item.sourceNodeId));
        const terminals = nodes.filter(node => !outgoing.has(node.id));
        nodes.push(end); for (const terminal of terminals) if (terminal.id !== end.id) transitions.push(createFlowTransition(terminal.id, end.id));
    }
}
function nodeRedKind(node: NodeRedNode): FlowNodeKind {
    const hydra = String(node.hydraKind ?? '');
    const all: FlowNodeKind[] = ['START','SUCCESS_END','ERROR_END','EVENT_STEP','COMMAND_STEP','ACTION_STEP','OPERATION_STEP','DECISION','MERGE','PARALLEL_SPLIT','JOIN','TIMEOUT','RETRY','WAIT_FOR_EVENT','MANUAL_STEP','COMPENSATION_STEP','FALLBACK','ERROR_HANDLER','SOURCE','FILTER','MAP','ROUTE','DEDUPLICATE','EMIT','WINDOW','AGGREGATE','SEQUENCE','PATTERN','ABSENCE'];
    if (all.includes(hydra as FlowNodeKind)) return hydra as FlowNodeKind;
    if (node.type === 'inject') return 'START';
    if (['mqtt in','websocket in','http in'].includes(node.type)) return 'SOURCE';
    if (node.type === 'switch') return 'FILTER';
    if (node.type === 'change') return 'MAP';
    if (node.type === 'delay') return 'TIMEOUT';
    if (node.type === 'catch') return 'ERROR_HANDLER';
    if (['mqtt out','websocket out','http response','link out'].includes(node.type)) return 'EMIT';
    if (node.type === 'debug') return 'SUCCESS_END';
    return 'COMMAND_STEP';
}
function nodeRedType(kind: FlowNodeKind): string {
    if (kind === 'START') return 'inject'; if (kind === 'SOURCE') return 'mqtt in'; if (kind === 'FILTER' || kind === 'DECISION' || kind === 'ROUTE') return 'switch'; if (kind === 'MAP') return 'change'; if (kind === 'TIMEOUT' || kind === 'RETRY' || kind === 'WAIT_FOR_EVENT') return 'delay'; if (kind === 'ERROR_HANDLER' || kind === 'ERROR_END') return 'catch'; if (kind === 'EMIT') return 'mqtt out'; if (kind === 'SUCCESS_END') return 'debug'; return 'function';
}
function compactNodeRedConfiguration(node: NodeRedNode): Record<string, unknown> { const result: Record<string, unknown> = {}; for (const [key, value] of Object.entries(node)) if (!['id','type','z','name','wires','x','y','hydraKind','hydraReferenceId','hydraConditionExpressionId','hydraConfiguration'].includes(key)) result[key] = value; return result; }
function resolveEventType(value: string, eventTypes: readonly EventTypeDefinition[]): string { const normalized = value.trim(); if (normalized === '') return ''; const match = eventTypes.find(item => item.id === normalized || item.name === normalized || `${item.namespace}.${item.id}` === normalized || item.name.toLowerCase() === normalized.toLowerCase()); return match?.id ?? ''; }
function eventName(id: string, eventTypes: readonly EventTypeDefinition[]): string { return eventTypes.find(item => item.id === id)?.name ?? id; }
function humanizeNodeRed(value: string): string { return value.split(/[-_ ]+/).map(part => part.charAt(0).toUpperCase() + part.slice(1)).join(' '); }
function isObject(value: unknown): value is Record<string, unknown> { return value !== null && typeof value === 'object' && !Array.isArray(value); }
