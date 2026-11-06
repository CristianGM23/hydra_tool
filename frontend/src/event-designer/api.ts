import { backendFetch } from '../api/backendFetch';
import { readValidationJson } from '../validation/http';
import type { EventArchitectureDiagramDocument, EventArchitectureDiagramLoadResult, EventModelLoadResult, EventModelSaveResponse, EventBindingModel, EventDefinitionModel } from './types';
import { normalizeEventModelLoadResult, normalizeEventModelSaveResponse } from './eventModelNormalization';
import { measureHydraDiagramAsyncOperation } from '../diagram/performanceTelemetry';
import { withHydraConnectionDefaults } from '../designer-ui/HydraConnectionVisual';
const route = (metamodelId: string): string => `/api/canonical-metamodels/${encodeURIComponent(metamodelId)}/events`;
export async function loadEventModels(metamodelId: string): Promise<EventModelLoadResult> {
    return normalizeEventModelLoadResult(await readValidationJson<EventModelLoadResult>(await backendFetch(route(metamodelId))));
}
export async function createEventModels(definitions: EventDefinitionModel, bindings: EventBindingModel): Promise<EventModelSaveResponse> {
    return normalizeEventModelSaveResponse(await readValidationJson<EventModelSaveResponse>(await backendFetch(route(definitions.metamodelId), { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ definitions, bindings }) })));
}
export async function replaceEventModels(definitions: EventDefinitionModel, definitionEtag: string, bindings: EventBindingModel, bindingEtag: string): Promise<EventModelSaveResponse> {
    const headers: Record<string, string> = { 'Content-Type': 'application/json' };
    if (definitionEtag !== '') headers['If-Match-Definitions'] = definitionEtag;
    if (bindingEtag !== '') headers['If-Match-Bindings'] = bindingEtag;
    return normalizeEventModelSaveResponse(await readValidationJson<EventModelSaveResponse>(await backendFetch(route(definitions.metamodelId), { method: 'PUT', headers, body: JSON.stringify({ definitions, bindings }) })));
}
export async function loadReservedEventTypes(): Promise<string[]> { return readValidationJson<string[]>(await backendFetch('/api/canonical-metamodels/events/reserved-types')); }

const diagramRoute = (metamodelId: string): string => `${route(metamodelId)}/diagram`;
export async function loadEventArchitectureDiagram(metamodelId: string): Promise<EventArchitectureDiagramLoadResult> {
    return readValidationJson<EventArchitectureDiagramLoadResult>(await backendFetch(diagramRoute(metamodelId)));
}
export async function saveEventArchitectureDiagram(metamodelId: string, diagram: EventArchitectureDiagramDocument, etag: string): Promise<EventArchitectureDiagramLoadResult> {
    const headers: Record<string, string> = { 'Content-Type': 'application/json' };
    if (etag !== '') headers['If-Match'] = etag;
    return measureHydraDiagramAsyncOperation(`event-architecture:${metamodelId}`, 'commit', async () =>
        readValidationJson<EventArchitectureDiagramLoadResult>(await backendFetch(diagramRoute(metamodelId), { method: 'PUT', headers, body: JSON.stringify({ ...diagram, edges: diagram.edges.map(withHydraConnectionDefaults) }) })),
        { nodeCount: diagram.nodes.length, edgeCount: diagram.edges.length });
}
