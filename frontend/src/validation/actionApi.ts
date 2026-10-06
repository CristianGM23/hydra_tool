import { backendFetch } from '../api/backendFetch';
import { readActionableApiError } from '../api/actionableError';
import type { ActionModel, ActionModelCheckReport, ActionModelSaveResponse } from './types';

import { readValidationJson } from './http';
import { normalizeActionCheckReport, normalizeActionModel, normalizeActionModelSaveResponse } from './validationContractNormalization';
const route = (metamodelId: string): string => `/api/canonical-metamodels/${encodeURIComponent(metamodelId)}/actions`;

export async function loadActionModel(metamodelId: string): Promise<{ actionModel: ActionModel; etag: string }> {
    const response = await backendFetch(route(metamodelId));
    return { actionModel: normalizeActionModel(await readValidationJson<unknown>(response)), etag: response.headers.get('ETag') ?? '' };
}
export async function createActionModel(model: ActionModel): Promise<ActionModelSaveResponse> {
    const response = await backendFetch(route(model.metamodelId), { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(model) });
    const result = await readValidationJson<unknown>(response);
    return normalizeActionModelSaveResponse(result, model, response.headers.get('ETag') ?? '');
}
export async function replaceActionModel(model: ActionModel, expectedEtag: string): Promise<ActionModelSaveResponse> {
    const headers: Record<string, string> = { 'Content-Type': 'application/json' };
    if (expectedEtag !== '') headers['If-Match'] = expectedEtag;
    const response = await backendFetch(route(model.metamodelId), { method: 'PUT', headers, body: JSON.stringify(model) });
    const result = await readValidationJson<unknown>(response);
    return normalizeActionModelSaveResponse(result, model, response.headers.get('ETag') ?? '');
}
export async function checkActionModel(metamodelId: string): Promise<ActionModelCheckReport> {
    return normalizeActionCheckReport(await readValidationJson<unknown>(await backendFetch(`${route(metamodelId)}/check`, { method: 'POST' })), `${metamodelId}-actions`);
}
export async function deleteActionModel(metamodelId: string, expectedEtag: string): Promise<void> {
    const headers: Record<string, string> = {};
    if (expectedEtag !== '') headers['If-Match'] = expectedEtag;
    const response = await backendFetch(route(metamodelId), { method: 'DELETE', headers });
    if (!response.ok) throw await readActionableApiError(response);
}
