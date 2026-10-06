import { backendFetch } from '../api/backendFetch';
import { readActionableApiError } from '../api/actionableError';
import type { PublicationArtifactRequest, PublicationChannel, PublicationResult,
    PublicationSigningMode, PublicationValidationReport, UpdateCheckResult } from './types';

export interface PublicationDraft {
    productId: string; version: string; channel: PublicationChannel; signingMode: PublicationSigningMode;
    distributionDirectory: string; updateBaseUrl: string; artifacts: PublicationArtifactRequest[];
    metadata: Record<string, string>;
}
async function json<T>(response: Response): Promise<T> {
    if (!response.ok) throw await readActionableApiError(response);
    return response.json() as Promise<T>;
}
export async function validatePublication(request: PublicationDraft): Promise<PublicationValidationReport> {
    return json(await backendFetch('/api/publications/validate', {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(request)
    }));
}
export async function publishDistribution(request: PublicationDraft): Promise<PublicationResult> {
    return json(await backendFetch('/api/publications/publish', {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(request)
    }));
}
export async function checkDistributionUpdate(productId: string, currentVersion: string,
        channel: PublicationChannel, platform: string): Promise<UpdateCheckResult> {
    return json(await backendFetch('/api/publications/check', {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ productId, currentVersion, channel, platform })
    }));
}
