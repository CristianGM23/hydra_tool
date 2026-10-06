import type { RemoteStorageLocationResolution, RemoteStorageProfileConfiguration, RemoteStorageProfileUpdateRequest, RemoteStorageProvider } from '../remote-storage/types';

export type Draft = RemoteStorageProfileUpdateRequest & { id: string };
export type DraftErrors = Partial<Record<'id' | 'displayName' | 'owner' | 'repository' | 'projectId' | 'branch' | 'rootPath' | 'repositoryUrl', string>>;
export const EMPTY_REPOSITORY_DRAFT: Draft = {
    id: '', displayName: '', provider: 'GITHUB', enabled: true,
    baseUrl: '', owner: '', repository: '', branch: 'main', projectId: '', driveId: '', oneDriveAppFolder: false,
    repositoryUrl: '', rootItemId: '', gitUsername: '', rootPath: '', apiVersion: '2026-03-10', maximumArtifactBytes: 0,
};

export function automaticProfileId(draft: Draft): string {
    const base = `${draft.provider}-${draft.displayName}`.toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g, '').replace(/[^a-z0-9._-]+/g, '-').replace(/^-+|-+$/g, '');
    return base.slice(0, 72);
}
export function toDraft(profile: RemoteStorageProfileConfiguration): Draft {
    return {
        id: profile.id, displayName: profile.displayName, provider: profile.provider, enabled: profile.enabled,
        baseUrl: profile.baseUrl, owner: profile.owner, repository: profile.repository, branch: profile.branch,
        projectId: profile.projectId, driveId: profile.driveId, oneDriveAppFolder: profile.oneDriveAppFolder,
        repositoryUrl: profile.repositoryUrl, rootItemId: profile.rootItemId ?? '', gitUsername: profile.gitUsername,
        rootPath: profile.rootPath, apiVersion: profile.apiVersion, maximumArtifactBytes: profile.maximumArtifactBytes,
    };
}
export function stripId(draft: Draft): RemoteStorageProfileUpdateRequest { const { id, ...request } = draft; void id; return request; }
export function applyLocationResolution(draft: Draft, resolution: RemoteStorageLocationResolution): Draft {
    return {
        ...draft,
        provider: resolution.provider,
        repositoryUrl: resolution.url,
        owner: resolution.owner || draft.owner,
        repository: resolution.repository || draft.repository,
        projectId: resolution.projectId || draft.projectId,
        branch: resolution.branch || (providerUsesBranch(resolution.provider) ? draft.branch || 'main' : ''),
        rootPath: resolution.rootPath || draft.rootPath,
        rootItemId: resolution.rootItemId || draft.rootItemId,
        oneDriveAppFolder: resolution.provider === 'ONEDRIVE' ? false : draft.oneDriveAppFolder,
    };
}
export function persistedMatchesDraft(profile: RemoteStorageProfileConfiguration, draft: Draft): boolean {
    const value = toDraft(profile);
    if (value.id !== draft.id || value.displayName !== draft.displayName || value.provider !== draft.provider || value.enabled !== draft.enabled) return false;
    if (value.repositoryUrl !== draft.repositoryUrl || value.rootItemId !== draft.rootItemId) return false;
    if (draft.provider === 'GITHUB') return value.owner === draft.owner && value.repository === draft.repository && value.branch === draft.branch && value.rootPath === draft.rootPath;
    if (draft.provider === 'GITLAB') return value.projectId === draft.projectId && value.branch === draft.branch && value.rootPath === draft.rootPath;
    if (draft.provider === 'GENERIC_GIT') return value.branch === draft.branch && value.rootPath === draft.rootPath;
    if (draft.provider === 'GOOGLE_DRIVE') return value.driveId === draft.driveId && value.rootPath === draft.rootPath;
    if (draft.provider === 'DROPBOX') return value.rootPath === draft.rootPath;
    return value.driveId === draft.driveId && value.rootPath === draft.rootPath && value.oneDriveAppFolder === draft.oneDriveAppFolder;
}
export function repositorySummary(profile: RemoteStorageProfileConfiguration): string {
    if (profile.repositoryUrl) return profile.repositoryUrl;
    if (profile.provider === 'GITHUB') return [profile.owner, profile.repository].filter(Boolean).join('/') || profile.id;
    if (profile.provider === 'GITLAB') return profile.projectId || profile.id;
    if (profile.provider === 'GENERIC_GIT') return profile.repositoryUrl || profile.id;
    if (profile.provider === 'GOOGLE_DRIVE') return profile.driveId || 'My Drive';
    if (profile.provider === 'DROPBOX') return profile.rootPath || 'Dropbox';
    return profile.driveId || 'My OneDrive';
}
export function providerBaseHint(provider: RemoteStorageProvider): string {
    if (provider === 'GITHUB') return 'https://api.github.com'; if (provider === 'GITLAB') return 'https://gitlab.com/api/v4';
    if (provider === 'ONEDRIVE') return 'https://graph.microsoft.com/v1.0'; if (provider === 'GOOGLE_DRIVE') return 'https://www.googleapis.com/drive/v3';
    if (provider === 'DROPBOX') return 'https://api.dropboxapi.com/2'; return 'https://git.example.org';
}
export function providerOptions(t: (value: string) => string) {
    return [
        { value: 'GITHUB', label: 'GitHub', description: t('Paste a GitHub repository, branch, folder or file URL.'), category: t('Git repositories') },
        { value: 'GITLAB', label: 'GitLab', description: t('Paste a GitLab project, branch, folder or file URL.'), category: t('Git repositories') },
        { value: 'GENERIC_GIT', label: t('Generic Git'), description: t('Paste an HTTPS Git repository URL for a compatible self-hosted service.'), category: t('Git repositories') },
        { value: 'ONEDRIVE', label: 'OneDrive', description: t('Paste a OneDrive or SharePoint folder/file URL; HYDRA resolves it server-side before transfer.'), category: t('Cloud storage') },
        { value: 'GOOGLE_DRIVE', label: t('Google Drive'), description: t('Paste a Google Drive folder or Google Docs/Sheets/Slides URL.'), category: t('Cloud storage') },
        { value: 'DROPBOX', label: 'Dropbox', description: t('Paste a Dropbox shared folder or file URL; HYDRA resolves it using server-side access.'), category: t('Cloud storage') },
    ];
}

export function validatePrimaryRepositoryUrl(value: string, t: (value: string) => string): string | undefined {
    const input = value.trim();
    if (input === '') return t('Repository or folder URL is required.');
    let parsed: URL;
    try { parsed = new URL(input); }
    catch { return t('Enter a complete HTTPS URL copied from the repository or folder page.'); }
    if (parsed.protocol.toLowerCase() !== 'https:') return t('Repository and folder URLs must use HTTPS.');
    if (parsed.username !== '' || parsed.password !== '') return t('Do not include a username, password or token inside the URL.');
    if (parsed.hostname.trim() === '') return t('The URL must include the provider host name.');
    const host = parsed.hostname.toLowerCase();
    const segments = parsed.pathname.split('/').filter(Boolean);
    if ((host === 'github.com' || host === 'www.github.com') && segments.length < 2) return t('A GitHub URL must include both the owner or organization and the repository name.');
    if ((host === 'gitlab.com' || host === 'www.gitlab.com') && segments.length < 2) return t('A GitLab URL must include the project namespace and project name.');
    if ((host === 'drive.google.com' || host === 'docs.google.com') && segments.length === 0) return t('Paste the URL of a specific Google Drive folder or Google document, not only the Drive home page.');
    if ((host === 'dropbox.com' || host === 'www.dropbox.com' || host === 'dl.dropboxusercontent.com') && segments.length === 0) return t('Paste the URL of a specific Dropbox folder or file, not only the Dropbox home page.');
    if ((host === 'onedrive.live.com' || host === '1drv.ms' || host.endsWith('.sharepoint.com')) && segments.length === 0 && parsed.search === '') return t('Paste the sharing or folder URL of the OneDrive or SharePoint item you want HYDRA to use.');
    return undefined;
}

export function validateDraft(draft: Draft, t: (value: string) => string, urlFirst = false): DraftErrors {
    const errors: DraftErrors = {};
    if (draft.id.trim() === '') errors.id = t('Repository profile ID is required.');
    if (draft.displayName.trim() === '') errors.displayName = t('Display name is required.');
    if (urlFirst) { const urlError = validatePrimaryRepositoryUrl(draft.repositoryUrl, t); if (urlError !== undefined) errors.repositoryUrl = urlError; }
    if (!urlFirst && draft.rootPath.trim() === '') errors.rootPath = t('Remote root path is required.');
    if (!urlFirst && draft.provider === 'GITHUB') { if (draft.owner.trim() === '') errors.owner = t('Owner or organization is required.'); if (draft.repository.trim() === '') errors.repository = t('Repository is required.'); }
    if (!urlFirst && draft.provider === 'GITLAB' && draft.projectId.trim() === '') errors.projectId = t('GitLab project ID or path is required.');
    if (draft.provider === 'GENERIC_GIT' && draft.repositoryUrl.trim() === '') errors.repositoryUrl = t('Generic Git repository URL is required.');
    if (providerUsesBranch(draft.provider) && draft.branch.trim() === '') errors.branch = t('Branch is required.');
    return errors;
}
export function repositoryTargetDescription(provider: RemoteStorageProvider, t: (value: string) => string): string {
    const providerName = providerVisibleName(provider, t);
    return `${providerName}: ${t('Advanced mode exposes the coordinates HYDRA derived from the primary URL. Edit them only for non-standard provider deployments or unusual repository layouts.')}`;
}
export function providerVisibleName(provider: RemoteStorageProvider, t: (value: string) => string): string {
    if (provider === 'GOOGLE_DRIVE') return t('Google Drive'); if (provider === 'DROPBOX') return 'Dropbox'; if (provider === 'GENERIC_GIT') return t('Generic Git');
    return provider === 'ONEDRIVE' ? 'OneDrive' : provider === 'GITLAB' ? 'GitLab' : 'GitHub';
}
export function isGitProvider(provider: RemoteStorageProvider): boolean { return provider === 'GITHUB' || provider === 'GITLAB' || provider === 'GENERIC_GIT'; }
export function providerUsesBranch(provider: RemoteStorageProvider): boolean { return isGitProvider(provider); }
export function defaultRootHint(provider: RemoteStorageProvider, t: (value: string) => string): string {
    if (provider === 'DROPBOX') return '/Apps/HYDRA'; if (provider === 'GOOGLE_DRIVE') return t('HYDRA/exports'); return 'HYDRA';
}
export function providerTargetReady(draft: Draft, errors: DraftErrors, urlFirst = false): boolean {
    if (urlFirst) return draft.repositoryUrl.trim() !== '' && errors.repositoryUrl === undefined;
    if (draft.rootPath.trim() === '') return false;
    if (draft.provider === 'GITHUB') return errors.owner === undefined && errors.repository === undefined;
    if (draft.provider === 'GITLAB') return errors.projectId === undefined;
    if (draft.provider === 'GENERIC_GIT') return errors.repositoryUrl === undefined && errors.branch === undefined;
    return !providerUsesBranch(draft.provider) || errors.branch === undefined;
}
export function providerCompletionSummary(draft: Draft, t: (value: string) => string): string {
    if (draft.repositoryUrl) return `${providerVisibleName(draft.provider, t)} · ${draft.repositoryUrl}`;
    if (draft.provider === 'GITHUB') return `${draft.owner}/${draft.repository} · ${t('branch')} ${draft.branch}`;
    if (draft.provider === 'GITLAB') return `${draft.projectId} · ${t('branch')} ${draft.branch}`;
    if (draft.provider === 'GENERIC_GIT') return `${draft.repositoryUrl} · ${t('branch')} ${draft.branch}`;
    if (draft.provider === 'GOOGLE_DRIVE') return `${draft.driveId || t('My Drive')} · ${draft.rootPath}`;
    if (draft.provider === 'DROPBOX') return `${t('Dropbox root')} · ${draft.rootPath}`;
    return `${draft.driveId || t('My OneDrive')} · ${draft.rootPath}`;
}
export function providerSecurityGuidance(provider: RemoteStorageProvider, t: (value: string) => string): string {
    if (provider === 'GITHUB' || provider === 'GITLAB' || provider === 'GENERIC_GIT') return t('Repository credentials and OAuth material remain server-side. The browser stores only the remote URL and non-secret coordinates.');
    return t('OAuth tokens remain in the server-side credential vault. Shared URLs are resolved only inside the trusted backend before a transfer is executed.');
}
