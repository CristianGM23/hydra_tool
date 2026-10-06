import { backendFetch } from '../api/backendFetch';
import { readActionableApiError } from '../api/actionableError';
import type { GeneratedProjectDefinition, GeneratedProjectExecutionReport,
    GeneratedProjectRegistry, GeneratedProjectRuntimeProfile, GeneratedProjectStage } from './types';

async function json<T>(response: Response): Promise<T> {
    if (!response.ok) throw await readActionableApiError(response);
    return response.json() as Promise<T>;
}

export async function loadGeneratedProjectProfiles(): Promise<GeneratedProjectRuntimeProfile[]> {
    return json(await backendFetch('/api/generated-projects/profiles'));
}

export async function loadGeneratedProjectRegistry(): Promise<GeneratedProjectRegistry> {
    return json(await backendFetch('/api/generated-projects'));
}

export async function registerGeneratedProject(project: GeneratedProjectDefinition): Promise<GeneratedProjectRegistry> {
    return json(await backendFetch('/api/generated-projects', {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(project)
    }));
}

export async function executeGeneratedProject(projectId: string, stages: GeneratedProjectStage[],
        dryRun: boolean, allowOptionalStages: boolean): Promise<GeneratedProjectExecutionReport> {
    return json(await backendFetch('/api/generated-projects/execute', {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ projectId, stages, dryRun, allowOptionalStages, continueAfterFailure: false })
    }));
}
