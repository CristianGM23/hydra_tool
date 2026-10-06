import { backendFetch } from '../api/backendFetch';
import { readActionableApiError } from '../api/actionableError';
import type { ModelValidationReport, ValidationCompilationResult, ValidationExecutionRequest, ValidationModel, ValidationModelCheckReport, ValidationModelSaveResponse } from './types';

import { readValidationJson } from './http';
import { normalizeValidationCheckReport, normalizeValidationModel, normalizeValidationModelSaveResponse } from './validationContractNormalization';
const route = (metamodelId: string): string => `/api/canonical-metamodels/${encodeURIComponent(metamodelId)}/validations`;

export async function loadValidationModel(metamodelId: string): Promise<{ validationModel: ValidationModel; etag: string }> {
    const response = await backendFetch(route(metamodelId));
    return { validationModel: normalizeValidationModel(await readValidationJson<unknown>(response)), etag: response.headers.get('ETag') ?? '' };
}
export async function createValidationModel(model: ValidationModel): Promise<ValidationModelSaveResponse> {
    const response = await backendFetch(route(model.metamodelId), { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(model) });
    const result = await readValidationJson<unknown>(response);
    return normalizeValidationModelSaveResponse(result, model, response.headers.get('ETag') ?? '');
}
export async function replaceValidationModel(model: ValidationModel, expectedEtag: string): Promise<ValidationModelSaveResponse> {
    const headers: Record<string, string> = { 'Content-Type': 'application/json' };
    if (expectedEtag !== '') headers['If-Match'] = expectedEtag;
    const response = await backendFetch(route(model.metamodelId), { method: 'PUT', headers, body: JSON.stringify(model) });
    const result = await readValidationJson<unknown>(response);
    return normalizeValidationModelSaveResponse(result, model, response.headers.get('ETag') ?? '');
}
export async function checkValidationModel(metamodelId: string): Promise<ValidationModelCheckReport> {
    return normalizeValidationCheckReport(await readValidationJson<unknown>(await backendFetch(`${route(metamodelId)}/check`, { method: 'POST' })), `${metamodelId}-validations`);
}
export async function deleteValidationModel(metamodelId: string, expectedEtag: string): Promise<void> {
    const headers: Record<string, string> = {};
    if (expectedEtag !== '') headers['If-Match'] = expectedEtag;
    const response = await backendFetch(route(metamodelId), { method: 'DELETE', headers });
    if (!response.ok) throw await readActionableApiError(response);
}


const runtimeRoute = (metamodelId: string): string => `${route(metamodelId)}/runtime`;

export async function compileValidationPlan(metamodelId: string): Promise<ValidationCompilationResult> {
    return readValidationJson<ValidationCompilationResult>(await backendFetch(`${runtimeRoute(metamodelId)}/compile`, { method: 'POST' }));
}

export async function runModelValidation(metamodelId: string, request: ValidationExecutionRequest): Promise<ModelValidationReport> {
    return readValidationJson<ModelValidationReport>(await backendFetch(`${runtimeRoute(metamodelId)}/validate`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(request)
    }));
}

export type ValidationDrawioArtifact = 'definition' | 'plan' | 'report';

export async function downloadValidationDrawio(metamodelId: string, artifact: ValidationDrawioArtifact, request?: ValidationExecutionRequest): Promise<void> {
    const url = `${runtimeRoute(metamodelId)}/drawio/${artifact}`;
    const init: RequestInit = artifact === 'report'
        ? { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(request) }
        : { method: 'GET' };
    const response = await backendFetch(url, init);
    if (!response.ok) throw await readActionableApiError(response);
    const blob = await response.blob();
    const objectUrl = URL.createObjectURL(blob);
    const anchor = document.createElement('a');
    anchor.href = objectUrl;
    anchor.download = `${metamodelId}-validation-${artifact}.drawio`;
    document.body.append(anchor);
    anchor.click();
    anchor.remove();
    URL.revokeObjectURL(objectUrl);
}
