import { backendFetch } from '../api/backendFetch';
import { readActionableApiError } from '../api/actionableError';
import type { ElementConflictDecision, RoundTripApplyResult, RoundTripResolutionPlanView } from './types';

function route(metamodelId: string, profileId: string): string {
    return `/api/canonical-metamodels/${encodeURIComponent(metamodelId)}/integrations/${encodeURIComponent(profileId)}/technology/sync/resolve`;
}

async function readJson<T>(response: Response): Promise<T> {
    if (!response.ok) throw await readActionableApiError(response);
    return response.json() as Promise<T>;
}

export async function prepareAdvancedRoundTrip(metamodelId: string, profileId: string, targetDirectory: string,
        decisions: ElementConflictDecision[]): Promise<RoundTripResolutionPlanView> {
    return readJson<RoundTripResolutionPlanView>(await backendFetch(route(metamodelId, profileId), {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ targetDirectory, decisions })
    }));
}

export async function applyAdvancedRoundTrip(metamodelId: string, profileId: string, targetDirectory: string,
        decisions: ElementConflictDecision[]): Promise<RoundTripApplyResult> {
    return readJson<RoundTripApplyResult>(await backendFetch(`${route(metamodelId, profileId)}/apply`, {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ targetDirectory, decisions })
    }));
}

export async function rollbackAdvancedRoundTrip(metamodelId: string, profileId: string, targetDirectory: string,
        operationId: string): Promise<RoundTripApplyResult> {
    return readJson<RoundTripApplyResult>(await backendFetch(`${route(metamodelId, profileId)}/rollback/${encodeURIComponent(operationId)}`, {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ targetDirectory })
    }));
}
