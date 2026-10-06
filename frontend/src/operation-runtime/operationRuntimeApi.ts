import { backendFetch } from '../api/backendFetch';
import { resolveBackendUrl } from '../api/backendConfig';
import type { OperationInvocationRequest, OperationInvocationResult, OperationRuntimeCatalog } from '../validation/types';
import { readValidationJson } from '../validation/http';

export function operationRuntimeCatalogUrl(metamodelId: string): string {
    return resolveBackendUrl(`/api/canonical-metamodels/${encodeURIComponent(metamodelId)}/operation-runtime`);
}

export async function loadOperationRuntimeCatalog(metamodelId: string): Promise<OperationRuntimeCatalog> {
    return readValidationJson<OperationRuntimeCatalog>(await backendFetch(operationRuntimeCatalogUrl(metamodelId)));
}

export async function invokeModelOperation(modelId: string, operationId: string, request: OperationInvocationRequest): Promise<OperationInvocationResult> {
    const response = await backendFetch(`/api/models/${encodeURIComponent(modelId)}/operations/${encodeURIComponent(operationId)}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(request)
    });
    return readValidationJson<OperationInvocationResult>(response);
}

export async function undoModelOperation(modelId: string): Promise<OperationInvocationResult> {
    return readValidationJson<OperationInvocationResult>(await backendFetch(`/api/models/${encodeURIComponent(modelId)}/operations/undo`, { method: 'POST' }));
}
