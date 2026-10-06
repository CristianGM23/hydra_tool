import { backendFetch } from '../api/backendFetch';
import { readActionableApiError } from '../api/actionableError';
import type { ExternalChangeCandidate, RepresentableImportPreview } from './types';
async function json<T>(response: Response): Promise<T> {
    if (!response.ok) throw await readActionableApiError(response);
    return response.json() as Promise<T>;
}
export async function loadRepresentableTechnologies(): Promise<string[]> {
    return json(await backendFetch('/api/representable-imports/technologies'));
}
export async function previewRepresentableImport(technology: string, metamodelId: string, strategy: string,
        changes: ExternalChangeCandidate[]): Promise<RepresentableImportPreview> {
    return json(await backendFetch('/api/representable-imports/preview', {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ technology, metamodelId, strategy, changes, options: {} })
    }));
}
export async function applyRepresentableImport(technology: string, metamodelId: string, strategy: string,
        changes: ExternalChangeCandidate[]): Promise<RepresentableImportPreview> {
    return json(await backendFetch('/api/representable-imports/apply', {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ technology, metamodelId, strategy, changes, options: {} })
    }));
}
