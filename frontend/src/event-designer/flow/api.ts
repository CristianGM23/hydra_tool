import { backendFetch } from '../../api/backendFetch';
import { readValidationJson } from '../../validation/http';
import type { EventFlowCheckReport, EventFlowDiagramDocument, EventFlowDiagramLoadResult, EventFlowLoadResult, EventFlowModel, EventFlowRuntimeSnapshot, EventFlowRuntimeStatus } from './types';
import { measureHydraDiagramAsyncOperation } from '../../diagram/performanceTelemetry';
import { withHydraConnectionDefaults } from '../../designer-ui/HydraConnectionVisual';
import { normalizeEventFlowModel } from './eventFlowNormalization';
const route = (metamodelId: string): string => `/api/canonical-metamodels/${encodeURIComponent(metamodelId)}/event-flows`;
/** Contrato real del backend para cargar/guardar flujos: el modelo viaja con la clave `eventFlowModel`. */
export interface EventFlowWireResult { eventFlowModel: EventFlowModel; etag: string; checkReport: EventFlowCheckReport; }
/** Convierte la respuesta del backend (`eventFlowModel`) al resultado interno del diseñador (`model`). */
export function toEventFlowLoadResult(wire: EventFlowWireResult): EventFlowLoadResult { return { model: normalizeEventFlowModel(wire.eventFlowModel), etag: wire.etag, checkReport: wire.checkReport }; }
export async function loadEventFlows(metamodelId: string): Promise<EventFlowLoadResult> { return toEventFlowLoadResult(await readValidationJson<EventFlowWireResult>(await backendFetch(`${route(metamodelId)}/optional`))); }
export async function createEventFlows(model: EventFlowModel): Promise<EventFlowLoadResult> { return toEventFlowLoadResult(await readValidationJson<EventFlowWireResult>(await backendFetch(route(model.metamodelId), { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(model) }))); }
export async function replaceEventFlows(model: EventFlowModel, etag: string): Promise<EventFlowLoadResult> { return toEventFlowLoadResult(await readValidationJson<EventFlowWireResult>(await backendFetch(route(model.metamodelId), { method: 'PUT', headers: { 'Content-Type': 'application/json', ...(etag === '' ? {} : { 'If-Match': etag }) }, body: JSON.stringify(model) }))); }
export async function checkEventFlows(model: EventFlowModel): Promise<EventFlowCheckReport> { return readValidationJson<EventFlowCheckReport>(await backendFetch(`${route(model.metamodelId)}/check`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(model) })); }
export async function loadEventFlowDiagram(metamodelId: string): Promise<EventFlowDiagramLoadResult> { return readValidationJson<EventFlowDiagramLoadResult>(await backendFetch(`${route(metamodelId)}/diagram`)); }
export async function saveEventFlowDiagram(diagram: EventFlowDiagramDocument, etag: string): Promise<EventFlowDiagramLoadResult> { return measureHydraDiagramAsyncOperation(`event-flow:${diagram.metamodelId}`, 'commit', async () => readValidationJson<EventFlowDiagramLoadResult>(await backendFetch(`${route(diagram.metamodelId)}/diagram`, { method: 'PUT', headers: { 'Content-Type': 'application/json', ...(etag === '' ? {} : { 'If-Match': etag }) }, body: JSON.stringify({ ...diagram, edges: diagram.edges.map(withHydraConnectionDefaults) }) })), { nodeCount: diagram.nodes.length, edgeCount: diagram.edges.length }); }
export async function startEventFlowRuntime(metamodelId: string): Promise<EventFlowRuntimeStatus> { return readValidationJson<EventFlowRuntimeStatus>(await backendFetch(`${route(metamodelId)}/runtime/start`, { method: 'POST' })); }
export async function stopEventFlowRuntime(metamodelId: string): Promise<EventFlowRuntimeStatus> { return readValidationJson<EventFlowRuntimeStatus>(await backendFetch(`${route(metamodelId)}/runtime/stop`, { method: 'POST' })); }
export async function loadEventFlowRuntimeStatus(metamodelId: string): Promise<EventFlowRuntimeStatus> { return readValidationJson<EventFlowRuntimeStatus>(await backendFetch(`${route(metamodelId)}/runtime/status`)); }
export async function loadEventFlowRuntimeTraces(metamodelId:string):Promise<import('./types').FlowExecutionTrace[]>{return readValidationJson<import('./types').FlowExecutionTrace[]>(await backendFetch(`${route(metamodelId)}/runtime/traces`));}
export async function tickEventFlowRuntime(metamodelId:string):Promise<import('./types').EventFlowRuntimeStatus>{await readValidationJson<unknown>(await backendFetch(`${route(metamodelId)}/runtime/tick`,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({now:new Date().toISOString()})}));return loadEventFlowRuntimeStatus(metamodelId);}
export async function clearEventFlowRuntimeState(metamodelId:string):Promise<void>{await backendFetch(`${route(metamodelId)}/runtime/state`,{method:'DELETE'});}

export async function loadEventFlowRuntimeState(metamodelId:string):Promise<EventFlowRuntimeSnapshot>{return readValidationJson<EventFlowRuntimeSnapshot>(await backendFetch(`${route(metamodelId)}/runtime/state`));}
