import { backendFetch } from '../api/backendFetch';
import { readActionableApiError } from '../api/actionableError';
import type { MetamodelLayoutRequest, MetamodelLayoutResponse } from './layoutTypes';

/**
 * Solicita al backend una organización automática del diagrama.
 *
 * @param request contiene el borrador y las opciones.
 * @returns el diagrama organizado.
 */
export async function layoutMetamodelDiagram(request: MetamodelLayoutRequest): Promise<MetamodelLayoutResponse> {
    const response = await backendFetch('/api/metamodel-layouts', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(request) });

    if(!response.ok) throw await readActionableApiError(response);

    return response.json() as Promise<MetamodelLayoutResponse>;
}
