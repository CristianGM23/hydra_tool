import { backendFetch } from '../api/backendFetch';
import { readActionableApiError } from '../api/actionableError';
import { remoteRequestHeaders } from '../remote-auth/api';
import type {
    RemoteGitConflictFileView,
    RemoteGitConflictResolutionRequest,
    RemoteGitConflictResolutionResult,
    RemoteGitOperation,
    RemoteGitOperationRequest,
    RemoteGitOperationResult,
    RemoteGitRepositoryTree,
} from './types';

async function readJson<T>(response: Response): Promise<T> {
    if (!response.ok) throw new Error((await readActionableApiError(response)).message);
    return response.json() as Promise<T>;
}

export async function executeRemoteGitOperation(operation: RemoteGitOperation, request: RemoteGitOperationRequest): Promise<RemoteGitOperationResult> {
    return readJson<RemoteGitOperationResult>(await backendFetch(`/api/remote-git/${operation}`, {
        method: 'POST',
        headers: await remoteRequestHeaders({ 'Content-Type': 'application/json' }),
        body: JSON.stringify(request),
    }));
}

export async function loadRemoteGitRepositoryTree(profileId: string, branch = ''): Promise<RemoteGitRepositoryTree> {
    const query = new URLSearchParams();
    if (branch.trim() !== '') query.set('branch', branch.trim());
    return readJson<RemoteGitRepositoryTree>(await backendFetch(`/api/remote-git/repository-tree/${encodeURIComponent(profileId)}?${query.toString()}`, {
        headers: await remoteRequestHeaders(),
    }));
}

export async function loadRemoteGitConflictView(profileId: string, relativePath: string): Promise<RemoteGitConflictFileView> {
    const query = new URLSearchParams({ path: relativePath });
    return readJson<RemoteGitConflictFileView>(await backendFetch(`/api/remote-git/conflicts/${encodeURIComponent(profileId)}?${query.toString()}`, {
        headers: await remoteRequestHeaders(),
    }));
}

export async function saveRemoteGitConflictResolution(request: RemoteGitConflictResolutionRequest): Promise<RemoteGitConflictResolutionResult> {
    return readJson<RemoteGitConflictResolutionResult>(await backendFetch('/api/remote-git/conflicts/resolve', {
        method: 'POST',
        headers: await remoteRequestHeaders({ 'Content-Type': 'application/json' }),
        body: JSON.stringify(request),
    }));
}
