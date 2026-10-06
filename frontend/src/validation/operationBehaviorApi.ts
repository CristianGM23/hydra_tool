import { backendFetch } from '../api/backendFetch';
import { resolveBackendUrl } from '../api/backendConfig';
import type { OperationBehaviorModel, OperationBehaviorSaveResponse } from './types';
import { readValidationJson } from './http';

const route = (metamodelId: string): string => `/api/canonical-metamodels/${encodeURIComponent(metamodelId)}/operation-behaviors`;
export async function loadOperationBehaviorModel(metamodelId: string): Promise<{ behaviorModel: OperationBehaviorModel; etag: string }> {
    const response = await backendFetch(route(metamodelId));
    return { behaviorModel: await readValidationJson<OperationBehaviorModel>(response), etag: response.headers.get('ETag') ?? '' };
}
export async function createOperationBehaviorModel(model: OperationBehaviorModel): Promise<OperationBehaviorSaveResponse> {
    const response = await backendFetch(route(model.metamodelId), { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(model) });
    const result = await readValidationJson<OperationBehaviorSaveResponse>(response);
    return { ...result, etag: response.headers.get('ETag') ?? result.etag };
}
export async function replaceOperationBehaviorModel(model: OperationBehaviorModel, expectedEtag: string): Promise<OperationBehaviorSaveResponse> {
    const headers: Record<string, string> = { 'Content-Type': 'application/json' };
    if (expectedEtag !== '') headers['If-Match'] = expectedEtag;
    const response = await backendFetch(route(model.metamodelId), { method: 'PUT', headers, body: JSON.stringify(model) });
    const result = await readValidationJson<OperationBehaviorSaveResponse>(response);
    return { ...result, etag: response.headers.get('ETag') ?? result.etag };
}
export async function checkOperationBehaviorModel(metamodelId: string): Promise<OperationBehaviorSaveResponse['validation']> {
    return readValidationJson<OperationBehaviorSaveResponse['validation']>(await backendFetch(`${route(metamodelId)}/check`, { method: 'POST' }));
}
export function operationBehaviorExportUrl(metamodelId: string): string { return resolveBackendUrl(`${route(metamodelId)}/export`); }
