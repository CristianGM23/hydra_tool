import { backendFetch } from '../api/backendFetch';
import { readActionableApiError } from '../api/actionableError';
import { remoteRequestHeaders } from '../remote-auth/api';
import type { ProjectSession } from '../project-explorer/types';
import type { RemoteArtifactDescriptor, RemoteLayerArtifact, RemoteProjectImportRequest, RemoteStorageConnectionTestResult, RemoteStorageLocationResolution, RemoteStorageProfileConfiguration, RemoteStorageProfileDescriptor, RemoteStorageProfileUpdateRequest, RemoteWriteMode } from './types';

async function json<T>(response: Response): Promise<T> {
    if (!response.ok) throw await readActionableApiError(response);
    return response.json() as Promise<T>;
}

export async function loadRemoteStorageProfiles(): Promise<RemoteStorageProfileDescriptor[]> {
    return json<RemoteStorageProfileDescriptor[]>(await backendFetch('/api/remote-storage/profiles'));
}

export async function loadRemoteStorageProfileConfigurations(): Promise<RemoteStorageProfileConfiguration[]> {
    return json<RemoteStorageProfileConfiguration[]>(await backendFetch('/api/remote-storage/profile-configurations', { cache: 'no-store' }));
}

export async function resolveRemoteStorageLocation(url: string): Promise<RemoteStorageLocationResolution> {
    return json<RemoteStorageLocationResolution>(await backendFetch('/api/remote-storage/resolve-location', {
        method: 'POST', headers: await remoteRequestHeaders({ 'Content-Type': 'application/json' }), body: JSON.stringify({ url }), cache: 'no-store',
    }));
}

export async function saveRemoteStorageProfile(profileId: string, request: RemoteStorageProfileUpdateRequest): Promise<RemoteStorageProfileConfiguration> {
    return json<RemoteStorageProfileConfiguration>(await backendFetch(`/api/remote-storage/profiles/${encodeURIComponent(profileId)}`, {
        method: 'PUT', headers: await remoteRequestHeaders({ 'Content-Type': 'application/json' }), body: JSON.stringify(request),
    }));
}

export async function testRemoteStorageConnection(profileId: string): Promise<RemoteStorageConnectionTestResult> {
    return json<RemoteStorageConnectionTestResult>(await backendFetch(`/api/remote-storage/profiles/${encodeURIComponent(profileId)}/test`, {
        method: 'POST', headers: await remoteRequestHeaders(), cache: 'no-store',
    }));
}

export async function deleteRemoteStorageProfile(profileId: string): Promise<void> {
    const response = await backendFetch(`/api/remote-storage/profiles/${encodeURIComponent(profileId)}`, { method: 'DELETE', headers: await remoteRequestHeaders() });
    if (!response.ok) throw await readActionableApiError(response);
}

export async function saveRemoteProject(profileId: string, remotePath: string, options: { mode?: RemoteWriteMode; expectedVersion?: string; commitMessage?: string } = {}): Promise<RemoteArtifactDescriptor> {
    return json<RemoteArtifactDescriptor>(await backendFetch('/api/remote-storage/projects/export', {
        method: 'POST', headers: await remoteRequestHeaders({ 'Content-Type': 'application/json' }), body: JSON.stringify({ profileId, remotePath, mode: options.mode ?? 'UPSERT', expectedVersion: options.expectedVersion ?? '', commitMessage: options.commitMessage ?? '' }),
    }));
}

export async function importRemoteProject(request: RemoteProjectImportRequest): Promise<ProjectSession> {
    return json<ProjectSession>(await backendFetch('/api/remote-storage/projects/import', {
        method: 'POST', headers: await remoteRequestHeaders({ 'Content-Type': 'application/json' }), body: JSON.stringify(request),
    }));
}

export async function saveRemoteLayerArtifact(profileId: string, remotePath: string, artifact: { fileName: string; content: BlobPart; mimeType?: string }, options: { mode?: RemoteWriteMode; expectedVersion?: string; commitMessage?: string } = {}): Promise<RemoteArtifactDescriptor> {
    const form = new FormData();
    const blob = artifact.content instanceof Blob ? artifact.content : new Blob([artifact.content], { type: artifact.mimeType ?? 'application/octet-stream' });
    form.append('file', blob, artifact.fileName);
    const query = new URLSearchParams({ profileId, remotePath, mode: options.mode ?? 'UPSERT', expectedVersion: options.expectedVersion ?? '', commitMessage: options.commitMessage ?? '' });
    return json<RemoteArtifactDescriptor>(await backendFetch(`/api/remote-storage/layers/export?${query.toString()}`, { method: 'POST', headers: await remoteRequestHeaders(), body: form }));
}

export async function loadRemoteFileArtifact(profileId: string, remotePath: string): Promise<{ file: File; version: string }> {
    const query = new URLSearchParams({ profileId, remotePath });
    const response = await backendFetch(`/api/remote-storage/layers/import?${query.toString()}`, { headers: await remoteRequestHeaders(), cache: 'no-store' });
    if (!response.ok) throw await readActionableApiError(response);
    const fileName = responseFileName(response, remotePath);
    const contentType = response.headers.get('content-type')?.split(';', 1)[0]?.trim() || 'application/octet-stream';
    // Construct the File from raw bytes instead of nesting a Response Blob. Besides
    // preserving binary payloads exactly, this avoids cross-realm Blob coercion in
    // browser/test environments where a Blob from fetch is not the same constructor
    // instance used by File and could otherwise become the text "[object Blob]".
    const bytes = await response.arrayBuffer();
    return { file: new File([bytes], fileName, { type: contentType }), version: response.headers.get('x-hydra-remote-version') ?? '' };
}

export async function loadRemoteLayerArtifact(profileId: string, remotePath: string): Promise<RemoteLayerArtifact> {
    const artifact = await loadRemoteFileArtifact(profileId, remotePath);
    return { fileName: artifact.file.name, content: await artifact.file.text(), version: artifact.version, contentType: artifact.file.type || 'text/plain' };
}

function responseFileName(response: Response, remotePath: string): string {
    const disposition = response.headers.get('content-disposition') ?? '';
    const utf8 = disposition.match(/filename\*=UTF-8''([^;]+)/i);
    if (utf8 !== null) return decodeURIComponent(utf8[1]);
    const ordinary = disposition.match(/filename="?([^";]+)"?/i);
    if (ordinary !== null) return ordinary[1];
    return remotePath.split('/').filter(Boolean).at(-1) ?? 'remote-artifact.bin';
}
