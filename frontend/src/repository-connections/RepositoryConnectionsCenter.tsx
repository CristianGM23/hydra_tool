import { useCallback, useEffect, useMemo, useState } from 'react';
import { HydraActionableErrorNotice, HydraButton, HydraCheckbox, HydraCreationModeSwitch, HydraFormField, HydraGuidedWorkflow, HydraRequiredFieldsLegend, HydraSelect, type HydraCreationMode } from '../design-system';
import { explainInputError, isActionableErrorMessage } from '../api/inputErrorExplanation';
import { formatActionableError } from '../api/actionableError';
import { useHydraLanguage } from '../i18n';
import { useHydraDialogs } from '../hooks/useHydraDialogs';
import { ExternalConnectionsSettings } from '../remote-auth/ExternalConnectionsSettings';
import type { RemoteAuthProfileDescriptor } from '../remote-auth/types';
import { closeProviderAuthorizationWindow, reserveProviderAuthorizationWindow, type ProviderAuthorizationWindow } from '../remote-auth/providerAuthorizationWindow';
import { RemoteGitOperationsPanel } from '../remote-git/RemoteGitOperationsPanel';
import { deleteRemoteStorageProfile, loadRemoteStorageProfileConfigurations, resolveRemoteStorageLocation, saveRemoteStorageProfile } from '../remote-storage/api';
import type { RemoteStorageProfileConfiguration, RemoteStorageProvider } from '../remote-storage/types';
import './repository-connections.css';
import { executeRepositoryConnectionTest, type RepositoryConnectionTestState } from './repositoryConnectionTest';
import { focusRepositoryField } from './repositoryConnectionFocus';
import { buildRepositoryConnectionGuidedSteps } from './RepositoryConnectionsGuidedSteps';
import { RepositoryProfileList } from './RepositoryProfileList';
import { EMPTY_REPOSITORY_DRAFT as EMPTY, applyLocationResolution, automaticProfileId, defaultRootHint, isGitProvider, persistedMatchesDraft, providerBaseHint, providerOptions, providerSecurityGuidance, providerTargetReady, providerUsesBranch, providerVisibleName, repositoryTargetDescription, stripId, toDraft, validateDraft, validatePrimaryRepositoryUrl, type Draft, type DraftErrors } from './repositoryProfileModel';
export function RepositoryConnectionsCenter({ embedded = false }: Readonly<{ embedded?: boolean }>) {
    const { t, language } = useHydraLanguage();
    const { requestDecision, dialogs } = useHydraDialogs();
    const [profiles, setProfiles] = useState<RemoteStorageProfileConfiguration[]>([]); const [selectedId, setSelectedId] = useState(''); const [draft, setDraft] = useState<Draft>(EMPTY);
    const [busy, setBusy] = useState(false); const [message, setMessage] = useState('');
    const [connectionTestState, setConnectionTestState] = useState<RepositoryConnectionTestState>('IDLE'); const [authRevision, setAuthRevision] = useState(0);
    const [autoConnectProfileId, setAutoConnectProfileId] = useState(''); const [pendingAuthorizationWindow, setPendingAuthorizationWindow] = useState<ProviderAuthorizationWindow | null>(null); const [authorizationState, setAuthorizationState] = useState<RemoteAuthProfileDescriptor | null>(null);
    const [lastSavedId, setLastSavedId] = useState(''); const [showValidation, setShowValidation] = useState(false); const [creationMode, setCreationMode] = useState<HydraCreationMode>('GUIDED');
    const [guidedStep, setGuidedStep] = useState('identity'); const [resolvedUrl, setResolvedUrl] = useState(''); const [resolvingUrl, setResolvingUrl] = useState(false);
    const effectiveProfileId = selectedId || draft.id.trim() || automaticProfileId(draft);
    const validationErrors = useMemo(() => validateDraft({ ...draft, id: effectiveProfileId }, t, creationMode === 'GUIDED'), [creationMode, draft, effectiveProfileId, t]);
    const refresh = useCallback(async (preferredId = '') => {
        try {
            const loaded = await loadRemoteStorageProfileConfigurations();
            setProfiles(loaded);
            const id = preferredId || (selectedId !== '' && loaded.some(item => item.id === selectedId) ? selectedId : loaded[0]?.id ?? '');
            setSelectedId(id);
            const loadedDraft = id === '' ? { ...EMPTY } : toDraft(loaded.find(item => item.id === id)!);
            setDraft(loadedDraft);
            setResolvedUrl(loadedDraft.repositoryUrl);
            setShowValidation(false);
            setMessage('');
        } catch (error) {
            setMessage(explainInputError(error, t('Could not load repository configurations.'), 'repository connection'));
        }
    }, [selectedId, t]);
    useEffect(() => {
        let active = true;
        void loadRemoteStorageProfileConfigurations().then(loaded => {
            if (!active) return;
            setProfiles(loaded);
            const id = loaded[0]?.id ?? '';
            setSelectedId(id);
            const loadedDraft = id === '' ? { ...EMPTY } : toDraft(loaded[0]);
            setDraft(loadedDraft);
            setResolvedUrl(loadedDraft.repositoryUrl);
        }).catch(error => {
            if (active) setMessage(explainInputError(error, t('Could not load repository configurations.'), 'repository connection'));
        });
        return () => { active = false; };
    }, [t]);
    const selected = useMemo(() => profiles.find(item => item.id === selectedId), [profiles, selectedId]);
    const visibleErrors = showValidation ? validationErrors : {};
    function discardPendingAuthorizationWindow(): void {
        const launch = pendingAuthorizationWindow;
        setPendingAuthorizationWindow(null);
        if (launch !== null) closeProviderAuthorizationWindow(launch);
    }
    function prepareAccess(): void {
        discardPendingAuthorizationWindow();
        if (draft.provider !== 'GENERIC_GIT') setPendingAuthorizationWindow(reserveProviderAuthorizationWindow(t('Connect an external account'), t('Opening the provider sign-in…'), language));
        void save(true);
    }
    function choose(profile: RemoteStorageProfileConfiguration): void {
        setSelectedId(profile.id); setDraft(toDraft(profile));
        setShowValidation(false); setMessage('');
        setConnectionTestState('IDLE'); setLastSavedId('');
        setGuidedStep('identity'); setResolvedUrl(profile.repositoryUrl);
        setAuthorizationState(null); setAutoConnectProfileId('');
        discardPendingAuthorizationWindow();
    }
    function newProfile(): void {
        setSelectedId(''); setDraft({ ...EMPTY });
        setShowValidation(false);
        setMessage(''); setConnectionTestState('IDLE');
        setLastSavedId('');
        setGuidedStep('identity'); setResolvedUrl('');
        setAuthorizationState(null);
        setAutoConnectProfileId('');
        discardPendingAuthorizationWindow();
    }
    function update<K extends keyof Draft>(key: K, value: Draft[K]): void {
        setDraft(current => ({ ...current, [key]: value })); if (key === 'repositoryUrl') setResolvedUrl('');
        if (key === 'repositoryUrl' || key === 'provider') { setAuthorizationState(null); setAutoConnectProfileId(''); discardPendingAuthorizationWindow(); }
        setMessage('');
        setConnectionTestState('IDLE');
    }
    function showDraftErrors(errors: DraftErrors, authorizeAfterSave = false): boolean {
        if (Object.keys(errors).length === 0) return false;
        setShowValidation(true);
        const first = Object.entries(errors)[0];
        const invalidFields = Object.keys(errors);
        setMessage(formatActionableError(
            language === 'es' ? 'La conexión no está lista todavía.' : 'The connection is not ready yet.',
            first?.[0] ?? '',
            first?.[1] ?? (language === 'es' ? 'Hay uno o más campos que deben completarse o corregirse.' : 'One or more fields must be completed or corrected.'),
            language === 'es' ? 'Corrige primero el campo indicado. HYDRA no contactará con ningún proveedor hasta que la URL sea completa y reconocible. Después pulsa Reconocer destino, comprueba el nombre de la conexión y continúa con Acceso.' : 'Correct the indicated field first. HYDRA will not contact a provider until the URL is complete and recognizable. Then press Recognize destination, verify the connection name and continue to Access.',
            [language === 'es' ? `Campos a revisar: ${invalidFields.join(', ')}.` : `Fields to review: ${invalidFields.join(', ')}.`]
        ));
        window.requestAnimationFrame(() => focusRepositoryField(first?.[0] ?? ''));
        if (authorizeAfterSave) discardPendingAuthorizationWindow();
        return true;
    }
    async function resolveLocationValue(value = draft.repositoryUrl): Promise<Draft | null> {
        const localUrlError = validatePrimaryRepositoryUrl(value, t);
        if (localUrlError !== undefined) {
            setResolvedUrl('');
            showDraftErrors({ repositoryUrl: localUrlError });
            return null;
        }
        setResolvingUrl(true);
        try {
            const resolution = await resolveRemoteStorageLocation(value.trim());
            const next = applyLocationResolution(draft, resolution);
            setDraft(next);
            setResolvedUrl(resolution.url);
            setShowValidation(false);
            setMessage(`${t('Destination recognized')}: ${providerVisibleName(resolution.provider, t)} · ${resolution.summary}`);
            setConnectionTestState('IDLE');
            return next;
        } catch (error) {
            setResolvedUrl('');
            setMessage(explainInputError(error, t('Could not recognize the remote URL.'), 'repository connection'));
            return null;
        } finally { setResolvingUrl(false); }
    }
    async function save(authorizeAfterSave = false): Promise<boolean> {
        setBusy(true);
        setMessage('');
        setLastSavedId('');
        try {
            let nextDraft = { ...draft };
            const id = selectedId || nextDraft.id.trim() || automaticProfileId(nextDraft);
            nextDraft = { ...nextDraft, id };
            const localErrors = validateDraft(nextDraft, t, creationMode === 'GUIDED');
            if (showDraftErrors(localErrors, authorizeAfterSave)) return false;
            if (creationMode === 'GUIDED') {
                const recognized = resolvedUrl !== '' && resolvedUrl === nextDraft.repositoryUrl;
                if (!recognized) {
                    const resolution = await resolveRemoteStorageLocation(nextDraft.repositoryUrl.trim());
                    nextDraft = applyLocationResolution(nextDraft, resolution);
                    setDraft(nextDraft);
                    setResolvedUrl(resolution.url);
                }
            } else if (nextDraft.repositoryUrl.trim() !== '') {
                const resolution = await resolveRemoteStorageLocation(nextDraft.repositoryUrl.trim());
                nextDraft = applyLocationResolution(nextDraft, resolution);
                setDraft(nextDraft);
                setResolvedUrl(resolution.url);
            }
            const resolvedErrors = validateDraft(nextDraft, t, creationMode === 'GUIDED');
            if (showDraftErrors(resolvedErrors, authorizeAfterSave)) return false;
            await saveRemoteStorageProfile(id, stripId(nextDraft));
            const loaded = await loadRemoteStorageProfileConfigurations();
            const persisted = loaded.find(item => item.id === id);
            if (persisted === undefined || !persistedMatchesDraft(persisted, nextDraft)) throw new Error(t('The repository was sent to the server but could not be verified after saving.'));
            setProfiles(loaded);
            setSelectedId(persisted.id);
            setDraft(toDraft(persisted));
            setResolvedUrl(persisted.repositoryUrl);
            setShowValidation(false);
            const stillAuthorized = authorizationState?.profileId === persisted.id && authorizationState.provider === persisted.provider && authorizationState.connected;
            if (!stillAuthorized) setAuthorizationState(null);
            setAuthRevision(value => value + 1);
            setLastSavedId(persisted.id);
            if (authorizeAfterSave && persisted.provider !== 'GENERIC_GIT') setAutoConnectProfileId(persisted.id);
            setConnectionTestState('IDLE');
            setMessage(stillAuthorized || persisted.provider === 'GENERIC_GIT'
                ? t('Repository connection saved and verified. It is ready for compatible import and export operations.')
                : t('Destination saved. Complete provider authorization in the Access step before using this connection.'));
            return true;
        } catch (error) {
            if (authorizeAfterSave) discardPendingAuthorizationWindow();
            setMessage(explainInputError(error, t('Could not save repository configuration.'), 'repository connection'));
            return false;
        } finally { setBusy(false); }
    }
    async function testConnection(): Promise<void> {
        if (selected === undefined || !persistedMatchesDraft(selected, draft)) {
            setMessage(t('Save the repository configuration before testing the connection.')); setConnectionTestState('FAILED'); return;
        }
        if (selected.provider !== 'GENERIC_GIT' && authorizationState?.connected !== true) { setConnectionTestState('IDLE'); prepareAccess(); return; }
        setBusy(true); setConnectionTestState('RUNNING'); setMessage(t('Testing repository connection…'));
        const outcome = await executeRepositoryConnectionTest(selected, t);
        setConnectionTestState(outcome.state); setMessage(outcome.message); setBusy(false);
    }
    async function remove(): Promise<void> {
        if (selected === undefined) return;
        if (!await requestDecision({ title: t('Delete repository profile?'), description: `${t('Delete repository profile')} "${selected.displayName}". ${t('The saved destination will no longer be available to import or export operations.')}`, confirmLabel: t('Delete'), cancelLabel: t('Cancel'), danger: true })) return;
        setBusy(true);
        setMessage('');
        try {
            await deleteRemoteStorageProfile(selected.id);
            setSelectedId('');
            setDraft({ ...EMPTY });
            await refresh();
            setAuthorizationState(null);
            setAutoConnectProfileId('');
            discardPendingAuthorizationWindow();
            setAuthRevision(value => value + 1);
            setMessage(t('Repository configuration deleted.'));
        } catch (error) {
            setMessage(explainInputError(error, t('Could not delete repository configuration.'), 'repository connection'));
        } finally {
            setBusy(false);
        }
    }
    const identitySection = <section className="repository-profile-section">
        <header>
            <strong>{t('Profile identity')}</strong>
            <span>{t('Start by giving the profile a stable technical ID, a clear visible name and a provider. These three values are what other parts of HYDRA use to recognize the destination safely and consistently.')}</span>
        </header>
        <div className="repository-profile-grid">
            <HydraFormField
                error={visibleErrors.id}
                example="github-main"
                help={t('Technical identifier reused internally by importers, exporters and future automations. It should be short, stable and unique because changing it later may break existing references.')}
                hint={t('Use lowercase letters, numbers, hyphen, underscore or dot. Example: github-main or dropbox-team-assets.')}
                label={t('Profile ID')}
                required
                targetId="repository-profile-id"
            >
                <input
                    aria-invalid={visibleErrors.id !== undefined}
                    disabled={selected !== undefined}
                    id="repository-profile-id"
                    placeholder="github-main"
                    required
                    value={selected !== undefined ? selected.id : draft.id}
                    onChange={event => update('id', event.target.value)}
                />
            </HydraFormField>
            <HydraFormField
                error={visibleErrors.displayName}
                example={t('Main source repository')}
                help={t('Friendly name shown to end users in menus, pickers and export dialogs. Write the business meaning here, not the secret or technical credential name.')}
                hint={t('Examples: Main source repository, Shared course materials, Team Dropbox deliverables.')}
                label={t('Display name')}
                required
                targetId="repository-display-name"
            >
                <input
                    aria-invalid={visibleErrors.displayName !== undefined}
                    id="repository-display-name"
                    placeholder={t('Main source repository')}
                    required
                    value={draft.displayName}
                    onChange={event => update('displayName', event.target.value)}
                />
            </HydraFormField>
            <HydraFormField
                example="GitHub"
                help={t('Provider family that will store or serve the artifacts. Choose it first because HYDRA adapts the following questions, validation rules and security guidance to the chosen platform.')}
                hint={t('GitHub and GitLab are repository-centric, OneDrive / Google Drive / Dropbox are file-space centric, and Generic Git targets a generic remote Git repository URL.')}
                label={t('Provider')}
                required
                targetId="repository-provider"
            >
                <HydraSelect
                    id="repository-provider"
                    ariaLabel={t('Provider')}
                    required
                    showFilter={false}
                    value={draft.provider}
                    options={providerOptions(t)}
                    onChange={value => update('provider', value as RemoteStorageProvider)}
                />
            </HydraFormField>
            <HydraFormField
                help={t('Controls whether compatible import and export dialogs may offer this profile to users. Disable it when you want to keep the configuration stored but temporarily unavailable.')}
                hint={t('Disabling a profile does not delete it and does not expose or erase the server-side credentials bound to it.')}
                label={t('Availability')}
                targetId="repository-enabled"
            >
                <HydraCheckbox
                    checked={draft.enabled}
                    id="repository-enabled"
                    label={t('Enable this repository for exports and imports')}
                    onChange={event => update('enabled', event.target.checked)}
                />
            </HydraFormField>
        </div>
    </section>;
    const targetSection = <section className="repository-profile-section">
        <header>
            <strong>{t('Repository target')}</strong>
            <span>{repositoryTargetDescription(draft.provider, t)}</span>
        </header>
        <div className="repository-profile-grid">
            {draft.provider !== 'GENERIC_GIT' && <HydraFormField
                className="repository-wide-field"
                error={visibleErrors.repositoryUrl}
                example={draft.provider === 'GITHUB' ? 'https://github.com/organization/repository' : draft.provider === 'GITLAB' ? 'https://gitlab.com/group/project' : draft.provider === 'GOOGLE_DRIVE' ? 'https://drive.google.com/drive/folders/…' : draft.provider === 'ONEDRIVE' ? 'https://1drv.ms/…' : 'https://www.dropbox.com/…'}
                help={t('Primary user-facing URL for this destination. In Advanced mode you can inspect or override the technical coordinates below, but HYDRA will still validate the URL and derive safe coordinates when the profile is saved.')}
                label={t('Primary remote URL')}
                targetId="repository-primary-url"
            >
                <input id="repository-primary-url" inputMode="url" placeholder="https://…" value={draft.repositoryUrl} onChange={event => update('repositoryUrl', event.target.value)} />
            </HydraFormField>}
            {draft.provider === 'GITHUB' && <>
                <HydraFormField
                    error={visibleErrors.owner}
                    example="my-organization"
                    help={t('Organization or personal owner that contains the GitHub repository. HYDRA combines this value with the repository name to build the safe target coordinates stored in the profile.')}
                    hint={t('Example: openai, university-lab or your personal GitHub account name.')}
                    label={t('Owner or organization')}
                    required
                    targetId="repository-owner"
                >
                    <input aria-invalid={visibleErrors.owner !== undefined} id="repository-owner" placeholder="my-organization" required value={draft.owner} onChange={event => update('owner', event.target.value)} />
                </HydraFormField>
                <HydraFormField
                    error={visibleErrors.repository}
                    example="models"
                    help={t('Name of the GitHub repository that will receive exports and from which imports may later be read.')}
                    hint={t('Use only the repository name, not the full URL. Example: models or hydra-artifacts.')}
                    label={t('Repository')}
                    required
                    targetId="repository-name"
                >
                    <input aria-invalid={visibleErrors.repository !== undefined} id="repository-name" placeholder="models" required value={draft.repository} onChange={event => update('repository', event.target.value)} />
                </HydraFormField>
            </>}
            {draft.provider === 'GITLAB' && <HydraFormField
                className="repository-wide-field"
                error={visibleErrors.projectId}
                example="group/project"
                help={t('GitLab project identifier or full path used by the API. HYDRA stores this as a safe coordinate and keeps any access secret outside the browser.')}
                hint={t('Examples: 4821, group/project or school/team/hydra-projects.')}
                label={t('GitLab project ID or path')}
                required
                targetId="repository-project-id"
            >
                <input aria-invalid={visibleErrors.projectId !== undefined} id="repository-project-id" placeholder="group/project" required value={draft.projectId} onChange={event => update('projectId', event.target.value)} />
            </HydraFormField>}
            {draft.provider === 'GENERIC_GIT' && <>
            <HydraFormField
                className="repository-wide-field"
                error={visibleErrors.repositoryUrl}
                example="https://git.example.org/team/hydra-artifacts.git"
                help={t('Full remote URL of the generic Git repository. Use this when the target is not GitHub or GitLab. HYDRA accepts only HTTPS remotes whose host is explicitly present in the server-side Git allowlist, and the credential remains server-managed.')}
                hint={t('Examples: https://git.example.org/team/hydra-artifacts.git or ssh-capable hosts adapted by a server-side connector.')}
                label={t('Generic Git repository URL')}
                required
                targetId="repository-generic-url"
            >
                <input aria-invalid={visibleErrors.repositoryUrl !== undefined} id="repository-generic-url" placeholder="https://git.example.org/team/hydra-artifacts.git" required value={draft.repositoryUrl} onChange={event => update('repositoryUrl', event.target.value)} />
            </HydraFormField>
            <HydraFormField
                example="oauth2"
                help={t('Optional HTTPS username used by the server-side Git credential prompt. The password or token is never stored in this form. Leave it empty to use the provider-safe default.') }
                hint={t('Use this only when your generic Git server requires a specific HTTP username together with a server-managed token.') }
                label={t('Git HTTPS username')}
                targetId="repository-git-username"
            >
                <input id="repository-git-username" placeholder="oauth2" value={draft.gitUsername} onChange={event => update('gitUsername', event.target.value)} />
            </HydraFormField>
            </>}
            {(draft.provider === 'ONEDRIVE' || draft.provider === 'GOOGLE_DRIVE') && <HydraFormField
                example={draft.provider === 'ONEDRIVE' ? t('Leave empty for My OneDrive') : t('Leave empty for My Drive')}
                help={draft.provider === 'ONEDRIVE'
                    ? t('Optional drive identifier. Leave it empty to target the default personal OneDrive, or fill it in when the exports belong to a specific shared drive.')
                    : t('Optional Google Drive or Shared Drive identifier. Leave it empty to target the user default drive, or provide the shared drive ID when the team works in a common space.')}
                hint={draft.provider === 'ONEDRIVE'
                    ? t('This is not a secret. It is only a coordinate pointing to the remote drive.')
                    : t('This is not a secret. It only identifies the target drive or shared drive.')}
                label={draft.provider === 'ONEDRIVE' ? t('Drive ID') : t('Drive or shared-drive ID')}
                targetId="repository-drive-id"
            >
                <input id="repository-drive-id" placeholder={draft.provider === 'ONEDRIVE' ? t('Leave empty for My OneDrive') : t('Leave empty for My Drive')} value={draft.driveId} onChange={event => update('driveId', event.target.value)} />
            </HydraFormField>}
            {draft.provider === 'ONEDRIVE' && <HydraFormField
                help={t('Stores exports in the application-scoped OneDrive folder instead of the drive root. This reduces accidental mixing with unrelated files and is often the safest default for per-application isolation.')}
                label={t('OneDrive folder mode')}
                targetId="repository-app-folder"
            >
                <HydraCheckbox checked={draft.oneDriveAppFolder} id="repository-app-folder" label={t('Use the application folder')} onChange={event => update('oneDriveAppFolder', event.target.checked)} />
            </HydraFormField>}
            {providerUsesBranch(draft.provider) && <HydraFormField
                error={visibleErrors.branch}
                example="main"
                help={t('Branch that HYDRA reads from and writes to. Keeping one explicit branch per profile makes remote exports predictable and easier to audit.')}
                hint={t('For Git platforms the usual default is main. Use a dedicated artifacts branch if you want stronger separation.')}
                label={t('Branch')}
                required
                targetId="repository-branch"
            >
                <input aria-invalid={visibleErrors.branch !== undefined} id="repository-branch" placeholder="main" required value={draft.branch} onChange={event => update('branch', event.target.value)} />
            </HydraFormField>}
            <HydraFormField
                error={visibleErrors.rootPath}
                example={defaultRootHint(draft.provider, t)}
                help={t('Remote folder path inside the chosen provider where HYDRA will place the exported artifacts and from which imports may later be resolved. Think of it as the workspace root for this profile.')}
                hint={t('Use a stable folder such as HYDRA, projects/hydra or course-2026/deliverables. Relative traversal like ../ is blocked for safety.')}
                label={t('Remote root path')}
                required
                targetId="repository-root-path"
            >
                <input aria-invalid={visibleErrors.rootPath !== undefined} id="repository-root-path" placeholder={defaultRootHint(draft.provider, t)} required value={draft.rootPath} onChange={event => update('rootPath', event.target.value)} />
            </HydraFormField>
        </div>
    </section>;
    const compatibilitySection = <section className="repository-profile-section">
        <header>
            <strong>{t('Compatibility and security')}</strong>
            <span>{t('Finish by reviewing optional provider overrides, artifact size limits and secure external connections. These controls refine behaviour without exposing credentials in the browser.')}</span>
        </header>
        <div className="repository-profile-grid">
            {(draft.provider === 'GITHUB' || draft.provider === 'GITLAB' || draft.provider === 'ONEDRIVE' || draft.provider === 'GOOGLE_DRIVE' || draft.provider === 'DROPBOX' || draft.provider === 'GENERIC_GIT') && <HydraFormField
                className="repository-wide-field"
                example={providerBaseHint(draft.provider)}
                help={t('Optional API or service base URL. Leave it empty for the official provider endpoint. Use it only when your organization exposes a compatible self-hosted deployment or a trusted internal gateway.')}
                hint={t('This field customizes the endpoint, not the credential. Secrets stay on the server side or in the secure credential vault.')}
                label={t('Custom API base URL')}
                targetId="repository-base-url"
            >
                <input id="repository-base-url" placeholder={providerBaseHint(draft.provider)} value={draft.baseUrl} onChange={event => update('baseUrl', event.target.value)} />
            </HydraFormField>}
            {draft.provider === 'GITHUB' && <HydraFormField
                example="2026-03-10"
                help={t('Pinned GitHub REST API version. Use it when you want stronger reproducibility against future API changes. Leave the default unless your administrator requires another version.')}
                label={t('GitHub API version')}
                targetId="repository-api-version"
            >
                <input id="repository-api-version" placeholder="2026-03-10" value={draft.apiVersion} onChange={event => update('apiVersion', event.target.value)} />
            </HydraFormField>}
            <HydraFormField
                example="0"
                help={t('Maximum artifact size accepted for this profile. Use 0 to inherit the global backend limit. This lets you tighten certain profiles without changing the global policy.')}
                hint={t('Useful when a destination should only receive small deliverables or when an administrator wants profile-specific guardrails.')}
                label={t('Maximum artifact size in bytes')}
                targetId="repository-max-bytes"
            >
                <input id="repository-max-bytes" min="0" type="number" value={draft.maximumArtifactBytes} onChange={event => update('maximumArtifactBytes', Math.max(0, Number(event.target.value) || 0))} />
            </HydraFormField>
        </div>
        <div className="repository-security-banner">
            <strong>{t('Security model')}</strong>
            <p>{providerSecurityGuidance(draft.provider, t)}</p>
        </div>
        <div className="repository-connections-step-panel">
            <ExternalConnectionsSettings key={authRevision} provider={draft.provider} profileId={selected?.id ?? effectiveProfileId} onStateChange={setAuthorizationState} />
        </div>
    </section>;
    const gitOperationsSection = isGitProvider(draft.provider) ? <section className="repository-profile-section repository-git-section">
        <header>
            <strong>{t('Git operations')}</strong>
            <span>{t('Once the profile has been saved, HYDRA can operate on the currently open local project with bounded Git actions: initialize, fetch, pull, push, commit, branch, checkout, merge and abort merge/rebase.')}</span>
        </header>
        {selected === undefined
            ? <div className="repository-security-banner"><strong>{t('Save the profile first')}</strong><p>{t('Git operations are enabled only after the repository coordinates have been saved, so the backend can resolve one stable profile and one server-side credential boundary.')}</p></div>
            : <RemoteGitOperationsPanel profileId={selected.id} defaultBranch={draft.branch || 'main'} />}
    </section> : null;
    const guidedSteps = buildRepositoryConnectionGuidedSteps({
        t, draft, visibleErrors, validationErrors, targetReady: providerTargetReady(draft, validationErrors, creationMode === 'GUIDED'), resolvedUrl, resolvingUrl, effectiveProfileId, authorizationProfileId: selected?.id ?? effectiveProfileId, authRevision, authorizationState,
        autoConnect: autoConnectProfileId !== '' && autoConnectProfileId === (selected?.id ?? effectiveProfileId), reservedAuthorizationWindow: pendingAuthorizationWindow,
        onAutoConnectHandled: () => setAutoConnectProfileId(''), onReservedAuthorizationWindowConsumed: () => setPendingAuthorizationWindow(null), onAuthorizationStateChange: setAuthorizationState,
        onRepositoryUrlChange: value => update('repositoryUrl', value), onDisplayNameChange: value => update('displayName', value), onEnabledChange: value => update('enabled', value),
        onResolve: () => { void resolveLocationValue(); }, profileSaved: selected !== undefined && persistedMatchesDraft(selected, draft), onPrepareAccess: prepareAccess,
    });
    return <section className={`repository-connections-center${embedded ? ' embedded' : ''}`}>
        {dialogs}
        <div className="repository-connections-layout">
            <RepositoryProfileList profiles={profiles} selectedId={selectedId} t={t} onCreate={newProfile} onChoose={choose} />
            <div className="repository-profile-editor">
                <div className="repository-profile-editor-heading">
                    <div>
                        <strong>{selected === undefined ? t('New repository profile') : selected.displayName}</strong>
                        <span>{t('Use the guided setup for the common case. Advanced mode keeps the complete provider configuration available when you need it.')}</span>
                    </div>
                    <div className="repository-profile-heading-tools">
                        <HydraRequiredFieldsLegend />
                        <HydraCreationModeSwitch className="repository-mode-switch" mode={creationMode} guidedLabel={t('Guided')} advancedLabel={t('Advanced / manual')} onChange={setCreationMode} />
                    </div>
                </div>
                {creationMode === 'GUIDED'
                    ? <HydraGuidedWorkflow className="repository-guided-workflow" title={t('Connect storage or repository')} description={t('Three steps: paste one URL, verify secure access, then save and reuse the connection.')} steps={guidedSteps} currentStepId={guidedStep} finishLabel={t('Save and verify connection')} onFinish={() => save()} onStepChange={setGuidedStep} />
                    : <div className="repository-profile-sections">
                        {identitySection}
                        {targetSection}
                        {compatibilitySection}
                        {gitOperationsSection}
                    </div>}
                {lastSavedId !== '' && <div className="repository-save-verified" role="status"><strong>{authorizationState?.connected || draft.provider === 'GENERIC_GIT' ? t('Connection saved') : t('Destination saved')}</strong><span>{authorizationState?.connected || draft.provider === 'GENERIC_GIT' ? t('HYDRA read the saved profile back from the backend successfully. The connection is ready to reuse.') : t('The destination coordinates are saved, but the provider still has to authorize this connection before HYDRA can use it.')}</span><code>{lastSavedId}</code></div>}
                <div className="repository-profile-actions">
                    <HydraButton disabled={busy} variant="primary" onClick={() => void save()}>{busy && connectionTestState !== 'RUNNING' ? t('Working…') : t('Save repository')}</HydraButton>
                    <HydraButton className={`repository-test-connection state-${connectionTestState.toLowerCase()}`} disabled={busy || selected === undefined || !persistedMatchesDraft(selected, draft)} variant="secondary" onClick={() => void testConnection()}>{selected !== undefined && selected.provider !== 'GENERIC_GIT' && authorizationState?.connected !== true ? t('Connect account') : connectionTestState === 'RUNNING' ? t('Testing connection…') : connectionTestState === 'SUCCESS' ? t('Connection verified') : t('Test connection')}</HydraButton>
                    <HydraButton disabled={busy || selected === undefined} variant="danger" onClick={() => void remove()}>{t('Delete repository')}</HydraButton>
                </div>
                {message !== '' && (isActionableErrorMessage(message) || connectionTestState === 'FAILED') ? <HydraActionableErrorNotice className="repository-profile-message" message={message} context="repository connection" onDismiss={() => { setMessage(''); if (connectionTestState === 'FAILED') setConnectionTestState('IDLE'); }} /> : message !== '' ? <p className="repository-profile-message" role="status">{message}</p> : null}
            </div>
        </div>
    </section>;
}
