import { backendFetch } from '../api/backendFetch';
import { readActionableApiError } from '../api/actionableError';
import type { StandaloneDistributionArtifact, StandaloneDistributionPreparationResult, StandaloneDistributionProfile, StandaloneDistributionValidationReport } from './types';

interface Request { editorDirectory: string; profile: StandaloneDistributionProfile; }
async function json<T>(path: string, request: Request): Promise<T> {
    const response = await backendFetch(`/api/standalone-distributions/${path}`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(request) });
    if (!response.ok) throw await readActionableApiError(response);
    return response.json() as Promise<T>;
}
export function planStandaloneDistribution(request: Request): Promise<StandaloneDistributionArtifact[]> { return json('plan', request); }
export function materializeStandaloneDistribution(request: Request): Promise<StandaloneDistributionPreparationResult> { return json('materialize', request); }
export function validateStandaloneDistribution(request: Request): Promise<StandaloneDistributionValidationReport> { return json('validate', request); }
export function assessStandaloneReleaseReadiness(request: Request): Promise<StandaloneDistributionValidationReport> { return json('release-readiness', request); }
export async function packageStandaloneDistribution(request: Request, packageLevel: 'project' | 'release-source' | 'web' = 'project'): Promise<{ blob: Blob; fileName: string; sha256: string }> {
    const endpoint = packageLevel === 'web' ? 'package-deployable-web' : packageLevel === 'release-source' ? 'package-release-source' : 'package';
    const response = await backendFetch(`/api/standalone-distributions/${endpoint}`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(request) });
    if (!response.ok) throw await readActionableApiError(response);
    const disposition = response.headers.get('Content-Disposition') ?? '';
    const match = /filename="?([^";]+)"?/i.exec(disposition);
    return { blob: await response.blob(), fileName: match?.[1] ?? 'hydra-editor.zip', sha256: response.headers.get('X-HYDRA-Distribution-SHA256') ?? '' };
}
