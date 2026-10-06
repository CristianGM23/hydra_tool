import { backendFetch } from '../api/backendFetch';
import { readActionableApiError } from '../api/actionableError';
import type { CapabilityLayerRollbackResult, CapabilityLayerTransactionPreview, CapabilityLayerTransactionRequest, CapabilityLayerTransactionResult, HydraCapabilityReadiness } from './types';

export function loadCapabilityReadiness(metamodelId: string): Promise<HydraCapabilityReadiness>;
export function loadCapabilityReadiness(contextType: 'METAMODEL'|'GRAMMAR', contextId: string): Promise<HydraCapabilityReadiness>;
export async function loadCapabilityReadiness(contextTypeOrId: string, contextId?: string): Promise<HydraCapabilityReadiness> {
    const contextType = contextId === undefined ? 'METAMODEL' : contextTypeOrId as 'METAMODEL'|'GRAMMAR';
    const resolvedId = contextId === undefined ? contextTypeOrId : contextId;
    const segment = contextType === 'GRAMMAR' ? 'grammar' : 'metamodel';
    const response = await backendFetch(`/api/hydra/capabilities/${segment}/${encodeURIComponent(resolvedId)}`);
    if (!response.ok) throw await readActionableApiError(response);
    return response.json() as Promise<HydraCapabilityReadiness>;
}


async function json<T>(response: Response): Promise<T> {
    if (!response.ok) throw await readActionableApiError(response);
    return response.json() as Promise<T>;
}

export async function previewCapabilityLayerTransaction(request: CapabilityLayerTransactionRequest): Promise<CapabilityLayerTransactionPreview> {
    return json(await backendFetch('/api/capability-packages/transactions/preview', {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ ...request, persist: false })
    }));
}

export async function applyCapabilityLayerTransaction(request: CapabilityLayerTransactionRequest): Promise<CapabilityLayerTransactionResult> {
    return json(await backendFetch('/api/capability-packages/transactions/apply', {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ ...request, persist: true })
    }));
}

export async function rollbackCapabilityLayerTransaction(transactionId: string, documentPaths: string[],
        expectedCurrentEtags: Record<string, string>): Promise<CapabilityLayerRollbackResult> {
    return json(await backendFetch('/api/capability-packages/transactions/rollback', {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ transactionId, documentPaths, expectedCurrentEtags })
    }));
}
