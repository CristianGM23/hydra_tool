import { HydraButton, HydraCheckbox, HydraFormField, type HydraGuidedStep } from '../design-system';
import { ExternalConnectionsSettings } from '../remote-auth/ExternalConnectionsSettings';
import type { RemoteAuthProfileDescriptor } from '../remote-auth/types';
import type { ProviderAuthorizationWindow } from '../remote-auth/providerAuthorizationWindow';
import type { Draft, DraftErrors } from './repositoryProfileModel';
import { providerCompletionSummary, providerVisibleName } from './repositoryProfileModel';

interface Args {
    t: (value: string) => string;
    draft: Draft;
    visibleErrors: DraftErrors;
    validationErrors: DraftErrors;
    targetReady: boolean;
    resolvedUrl: string;
    resolvingUrl: boolean;
    effectiveProfileId: string;
    authorizationProfileId: string;
    authRevision: number;
    authorizationState: RemoteAuthProfileDescriptor | null;
    autoConnect: boolean;
    reservedAuthorizationWindow: ProviderAuthorizationWindow | null;
    onAutoConnectHandled: () => void;
    onReservedAuthorizationWindowConsumed: () => void;
    onAuthorizationStateChange: (profile: RemoteAuthProfileDescriptor | null) => void;
    onRepositoryUrlChange: (value: string) => void;
    onDisplayNameChange: (value: string) => void;
    onEnabledChange: (value: boolean) => void;
    onResolve: () => void;
    profileSaved: boolean;
    onPrepareAccess: () => void;
}

/** Builds the three task-first steps without growing the repository orchestrator. */
export function buildRepositoryConnectionGuidedSteps(args: Args): HydraGuidedStep[] {
    const { t, draft, visibleErrors, validationErrors, targetReady, resolvedUrl, resolvingUrl, effectiveProfileId, authorizationProfileId, authRevision, authorizationState, profileSaved, autoConnect, reservedAuthorizationWindow } = args;
    const authorizationRequired = draft.provider !== 'GENERIC_GIT';
    const authorizationConnected = !authorizationRequired || (profileSaved && authorizationState?.profileId === authorizationProfileId && authorizationState.connected);
    const recognized=resolvedUrl !== '' && resolvedUrl === draft.repositoryUrl;
    const service=<section className="repository-profile-section repository-service-step repository-url-first-step">
        <header><strong>{t('Paste the repository or folder URL')}</strong><span>{t('Use the same HTTPS link you already copy from GitHub, GitLab, OneDrive, Google Drive/Docs or Dropbox. HYDRA recognizes the service and derives the technical coordinates for you.')}</span></header>
        <div className="repository-profile-grid">
            <HydraFormField className="repository-wide-field" error={visibleErrors.repositoryUrl} label={t('Repository or folder URL')} targetId="repository-guided-url" required help={t('Paste a repository, branch, folder, shared-drive or document URL. Embedded credentials are rejected. Cloud sharing links are resolved by the trusted backend before transfer.')} example="https://github.com/organization/repository">
                <input aria-invalid={visibleErrors.repositoryUrl !== undefined} id="repository-guided-url" inputMode="url" placeholder="https://…" required value={draft.repositoryUrl} onChange={event => args.onRepositoryUrlChange(event.target.value)} onBlur={() => { if (draft.repositoryUrl.trim() !== '' && !recognized) args.onResolve(); }} />
            </HydraFormField>
            <HydraFormField error={visibleErrors.displayName} label={t('Connection name')} targetId="repository-guided-name" required help={t('Friendly name shown to users. Example: “Main source repository” or “Shared course files”.')} example={t('Main source repository')}><input id="repository-guided-name" value={draft.displayName} onChange={event => args.onDisplayNameChange(event.target.value)} /></HydraFormField>
            <HydraFormField label={t('Availability')} targetId="repository-guided-enabled" help={t('Disable the connection when you want to keep its configuration without showing it in operational dialogs.')}><HydraCheckbox checked={draft.enabled} label={t('Make this connection available')} onChange={event => args.onEnabledChange(event.target.checked)} /></HydraFormField>
        </div>
        <div className="repository-url-resolution-row">
            <HydraButton size="small" loading={resolvingUrl} disabled={draft.repositoryUrl.trim() === '' || validationErrors.repositoryUrl !== undefined} onClick={args.onResolve}>{t('Recognize destination')}</HydraButton>
            {recognized && <span className="repository-url-resolution-success"><strong>{t('Recognized')}</strong> · {providerVisibleName(draft.provider, t)} · {providerCompletionSummary(draft, t)}</span>}
        </div>
        <details className="repository-advanced-disclosure"><summary>{t('What HYDRA derives from the URL')}</summary><p>{t('For Git repositories HYDRA extracts repository, branch and optional folder. For Google Drive/Docs it derives a safe root item. OneDrive and Dropbox sharing URLs are resolved by the secure backend immediately before transfer. You can inspect or override the derived coordinates in Advanced / manual mode.')}</p></details>
        <p className="repository-generated-id">{t('Technical ID')}: <code>{effectiveProfileId || t('generated when a name is entered')}</code></p>
    </section>;
    const access=<section className="repository-profile-section repository-access-step">
        <header><strong>{t('Access')}</strong><span>{t('The destination is connected only after the external service confirms access. Press Connect below; the account, password, passkey and MFA stay on the provider page.')}</span></header>
        <div className="repository-security-banner"><strong>{t('How access works')}</strong><p>{t('HYDRA opens the external service. Microsoft, GitHub, GitLab, Google or Dropbox asks for the account, password, Authenticator/MFA or consent according to its own policy. HYDRA only stores the resulting access authorization in its secure vault.')}</p><p>{t('The provider sign-in opens in a separate secure window. If your browser blocks it, HYDRA keeps a safe fallback link visible.')}</p></div>
        {!profileSaved ? <div className="repository-prepare-access"><div><strong>{t('Connect this repository')}</strong><span>{t('HYDRA saves only the non-secret destination coordinates first and then immediately opens the provider sign-in. You do not need a separate save step.')}</span></div><HydraButton variant="primary" onClick={args.onPrepareAccess}>{t('Connect')}</HydraButton></div> : <div className="repository-connections-step-panel"><ExternalConnectionsSettings key={authRevision} provider={draft.provider} profileId={authorizationProfileId} autoConnect={autoConnect} reservedAuthorizationWindow={reservedAuthorizationWindow} onAutoConnectHandled={args.onAutoConnectHandled} onReservedAuthorizationWindowConsumed={args.onReservedAuthorizationWindowConsumed} onStateChange={args.onAuthorizationStateChange} /></div>}
        <details className="repository-advanced-disclosure"><summary>{t('Advanced compatibility settings')}</summary><p>{t('Provider base URLs, API versions and artifact-size overrides are available in Advanced / manual mode. Most users can keep the safe defaults.')}</p></details>
    </section>;
    const valid=Object.keys(validationErrors).length === 0 && recognized && authorizationConnected;
    const review=<section className="repository-profile-section repository-review-section">
        <header><strong>{t('Review and save')}</strong><span>{t('The URL remains the primary user-facing coordinate. HYDRA stores only safe derived coordinates and keeps access authorizations or server-managed credentials outside the browser.')}</span></header>
        <div className="repository-review-grid">
            <div><strong>{t('Provider')}</strong><span>{providerVisibleName(draft.provider, t)}</span></div>
            <div><strong>{t('Primary URL')}</strong><span className="repository-review-url">{draft.repositoryUrl || t('Pending')}</span></div>
            <div><strong>{t('Destination summary')}</strong><span>{providerCompletionSummary(draft, t)}</span></div>
            <div><strong>{t('Availability')}</strong><span>{draft.enabled ? t('Enabled for operational dialogs') : t('Stored but disabled')}</span></div>
        </div>
    </section>;
    return [
        { id:'identity', title:t('Paste URL'), description:t('Give HYDRA one repository or folder URL and a recognizable connection name.'), checks:[
            { id:'identity-url',label:t('Primary URL provided'),complete:draft.repositoryUrl.trim()!=='' && validationErrors.repositoryUrl === undefined,detail:(validationErrors.repositoryUrl ?? draft.repositoryUrl.trim()) || t('Pending') },
            { id:'identity-resolved',label:t('Destination recognized'),complete:recognized,detail:recognized?providerVisibleName(draft.provider,t):t('Recognize the URL to continue') },
            { id:'identity-name',label:t('Visible display name defined'),complete:draft.displayName.trim()!=='',detail:draft.displayName.trim()||t('Pending') },
        ], content:service },
        { id:'access', title: t('Access'), description:t('Connect the recognized service without putting passwords or secrets in HYDRA.'), checks:[
            { id:'access-url',label:t('Destination coordinates are ready'),complete:targetReady&&recognized,detail:targetReady?providerCompletionSummary(draft,t):t('Recognize the URL first') },
            { id:'access-provider',label:t('External account connected'),complete:authorizationConnected,detail:authorizationConnected?t('The external service has authorized this connection'):profileSaved?(authorizationState?.oauthConfigured===false?t('An administrator must enable this provider once for the HYDRA installation'):t('Press Connect and complete sign-in or MFA on the provider page')):t('Press Connect; HYDRA will save the destination and open the provider sign-in automatically') },
        ], content:access },
        { id:'review', title:t('Review and save'), description:t('Confirm the URL, connected account and availability before saving.'), checks:[
            { id:'review-valid',label:t('Destination and provider access are ready'),complete:valid,detail:valid?t('Ready to save and use'):authorizationConnected?t('Some required fields still need attention'):t('The external account still needs to be connected') },
            { id:'review-availability',label:t('Availability decision recorded'),complete:true,detail:draft.enabled?t('The profile will be offered to compatible import/export dialogs'):t('The profile will stay stored but hidden from operational dialogs') },
        ], content:review },
    ];
}
