import { backendFetch } from '../api/backendFetch';
import { readActionableApiError } from '../api/actionableError';
import type { NativeToolchainCatalogEntry, TechnologyCompatibilityMatrix } from './types';

async function readJson<T>(response: Response): Promise<T> {
    if (!response.ok) throw await readActionableApiError(response);
    return response.json() as Promise<T>;
}

export async function loadNativeToolchainCatalog(): Promise<NativeToolchainCatalogEntry[]> {
    return readJson<NativeToolchainCatalogEntry[]>(await backendFetch('/api/toolchain-compatibility/catalog'));
}

export async function loadLatestCompatibilityMatrix(): Promise<TechnologyCompatibilityMatrix | null> {
    const response = await backendFetch('/api/toolchain-compatibility/latest');
    if (response.status === 204) return null;
    return readJson<TechnologyCompatibilityMatrix>(response);
}

export async function runNativeCompatibility(selectedKeys: string[]): Promise<TechnologyCompatibilityMatrix> {
    return readJson<TechnologyCompatibilityMatrix>(await backendFetch('/api/toolchain-compatibility/run', {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ selectedKeys })
    }));
}
