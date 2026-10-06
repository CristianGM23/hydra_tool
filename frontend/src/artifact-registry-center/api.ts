import { backendFetch } from '../api/backendFetch';
import { readActionableApiError } from '../api/actionableError';
import type { HydraArtifactDependencyGraph, HydraArtifactRegistrySnapshot } from './types';
async function json<T>(response: Response): Promise<T> {
    if (!response.ok) throw await readActionableApiError(response);
    return response.json() as Promise<T>;
}
export async function loadArtifactRegistry(): Promise<HydraArtifactRegistrySnapshot> {
    return json(await backendFetch('/api/artifact-registry'));
}
export async function loadArtifactGraph(): Promise<HydraArtifactDependencyGraph> {
    return json(await backendFetch('/api/artifact-registry/graph'));
}
export async function scanArtifacts(): Promise<HydraArtifactRegistrySnapshot> {
    return json(await backendFetch('/api/artifact-registry/scan', { method: 'POST' }));
}
