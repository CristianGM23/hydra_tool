import { backendFetch } from '../api/backendFetch';
import { readActionableApiError } from '../api/actionableError';

export interface TechnicalSettingDescriptor { key: string; category: string; value: string; mutable: boolean; description: string; }
export interface TechnicalSettingsSnapshot { activeProfiles: string[]; settings: TechnicalSettingDescriptor[]; }

export async function loadTechnicalSettings(): Promise<TechnicalSettingsSnapshot | null> {
    const response = await backendFetch('/api/system/technical-settings', { cache: 'no-store' });
    if (response.status === 404) return null;
    if (!response.ok) throw await readActionableApiError(response);
    return response.json() as Promise<TechnicalSettingsSnapshot>;
}

export async function updateTechnicalSettings(values: Record<string,string>): Promise<TechnicalSettingsSnapshot> {
    const response = await backendFetch('/api/system/technical-settings', {
        method: 'PATCH', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ values })
    });
    if (!response.ok) throw await readActionableApiError(response);
    return response.json() as Promise<TechnicalSettingsSnapshot>;
}
