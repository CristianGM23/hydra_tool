import type { ProjectSession } from '../project-explorer/types';

export type RemoteStorageProvider = 'GITHUB' | 'GITLAB' | 'ONEDRIVE' | 'GOOGLE_DRIVE' | 'DROPBOX' | 'GENERIC_GIT';
export type RemoteWriteMode = 'CREATE_ONLY' | 'UPDATE_ONLY' | 'UPSERT';

export interface RemoteStorageProfileDescriptor {
    id: string;
    displayName: string;
    provider: RemoteStorageProvider;
    enabled: boolean;
    configured: boolean;
    repository: string;
    branch: string;
    rootPath: string;
    maximumArtifactBytes: number;
}

export interface RemoteArtifactDescriptor {
    profileId: string;
    provider: RemoteStorageProvider;
    scope: 'PROJECT' | 'LAYER';
    path: string;
    version: string;
    size: number;
    contentType: string;
    webUrl: string;
    created: boolean;
}

export interface RemoteLayerArtifact {
    fileName: string;
    content: string;
    version: string;
    contentType: string;
}

export interface RemoteProjectImportRequest {
    profileId: string;
    remotePath: string;
    parentDirectory: string;
    projectName: string;
}

export type RemoteProjectImportResult = ProjectSession;

/** Safe editable repository configuration. Credentials are intentionally absent. */
export interface RemoteStorageProfileConfiguration {
    id: string;
    displayName: string;
    provider: RemoteStorageProvider;
    enabled: boolean;
    baseUrl: string;
    owner: string;
    repository: string;
    branch: string;
    projectId: string;
    driveId: string;
    oneDriveAppFolder: boolean;
    repositoryUrl: string;
    rootItemId: string;
    gitUsername: string;
    rootPath: string;
    apiVersion: string;
    maximumArtifactBytes: number;
    managedOverride: boolean;
}

export interface RemoteStorageProfileUpdateRequest {
    displayName: string;
    provider: RemoteStorageProvider;
    enabled: boolean;
    baseUrl: string;
    owner: string;
    repository: string;
    branch: string;
    projectId: string;
    driveId: string;
    oneDriveAppFolder: boolean;
    repositoryUrl: string;
    rootItemId: string;
    gitUsername: string;
    rootPath: string;
    apiVersion: string;
    maximumArtifactBytes: number;
}


export interface RemoteStorageLocationResolution {
    url: string;
    provider: RemoteStorageProvider;
    owner: string;
    repository: string;
    projectId: string;
    branch: string;
    rootPath: string;
    rootItemId: string;
    summary: string;
}

export interface RemoteStorageConnectionTestResult {
    profileId: string;
    provider: RemoteStorageProvider | null;
    connected: boolean;
    credentialsConfigured: boolean;
    message: string;
    testedAt: string;
}
