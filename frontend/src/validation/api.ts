import { backendFetch } from '../api/backendFetch';
import { readActionableApiError } from '../api/actionableError';
import type {
    CanonicalModelDocument,
    ExpressionEvaluationResult,
    ExpressionModel,
    ExpressionModelCheckReport,
    ExpressionModelSaveResponse
} from './types';

import { readValidationJson } from './http';
function route(metamodelId: string): string {
    return `/api/canonical-metamodels/${encodeURIComponent(metamodelId)}/expressions`;
}

export async function loadExpressionModel(metamodelId: string): Promise<{ expressionModel: ExpressionModel; etag: string }> {
    const response = await backendFetch(route(metamodelId));
    const expressionModel = await readValidationJson<ExpressionModel>(response);
    return { expressionModel, etag: response.headers.get('ETag') ?? '' };
}

export async function createExpressionModel(model: ExpressionModel): Promise<ExpressionModelSaveResponse> {
    const response = await backendFetch(route(model.metamodelId), {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(model)
    });
    const result = await readValidationJson<ExpressionModelSaveResponse>(response);
    return { ...result, etag: response.headers.get('ETag') ?? result.etag };
}

export async function replaceExpressionModel(model: ExpressionModel, expectedEtag: string): Promise<ExpressionModelSaveResponse> {
    const headers: Record<string, string> = { 'Content-Type': 'application/json' };
    if (expectedEtag !== '') headers['If-Match'] = expectedEtag;
    const response = await backendFetch(route(model.metamodelId), {
        method: 'PUT', headers, body: JSON.stringify(model)
    });
    const result = await readValidationJson<ExpressionModelSaveResponse>(response);
    return { ...result, etag: response.headers.get('ETag') ?? result.etag };
}

export async function checkExpressionModel(metamodelId: string): Promise<ExpressionModelCheckReport> {
    return readValidationJson<ExpressionModelCheckReport>(await backendFetch(`${route(metamodelId)}/check`, { method: 'POST' }));
}

export async function evaluateExpression(
    metamodelId: string,
    expressionId: string,
    model: CanonicalModelDocument,
    selfElementId: string
): Promise<ExpressionEvaluationResult> {
    return readValidationJson<ExpressionEvaluationResult>(await backendFetch(`${route(metamodelId)}/${encodeURIComponent(expressionId)}/evaluate`, {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ model, selfElementId })
    }));
}

export async function deleteExpressionModel(metamodelId: string, expectedEtag: string): Promise<void> {
    const headers: Record<string, string> = {};
    if (expectedEtag !== '') headers['If-Match'] = expectedEtag;
    const response = await backendFetch(route(metamodelId), { method: 'DELETE', headers });
    if (!response.ok) throw await readActionableApiError(response);
}
