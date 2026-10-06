import { useCallback, useEffect, useMemo, useState } from 'react';
import { HydraButton, HydraCheckbox, HydraFormField, HydraSelect } from '../design-system';
import { useHydraLanguage } from '../i18n';
import type { RemoteStorageProvider } from '../remote-storage/types';
import { loadRemoteOAuthProviderSetups, loadRemoteOAuthRegistrationKit, resetRemoteOAuthProviderSetup, saveRemoteOAuthProviderSetup } from './api';
import type { RemoteOAuthProviderSetupDescriptor, RemoteOAuthRegistrationKit } from './types';

interface Props { provider: RemoteStorageProvider; onChanged?: () => void | Promise<void>; }
type MicrosoftAccountMode = 'ORGANIZATION' | 'ORGANIZATIONS' | 'COMMON' | 'CONSUMERS';

function microsoftAccountMode(tenant: string): MicrosoftAccountMode {
    const normalized = tenant.trim().toLowerCase();
    if (normalized === 'organizations') return 'ORGANIZATIONS';
    if (normalized === 'common') return 'COMMON';
    if (normalized === 'consumers') return 'CONSUMERS';
    return 'ORGANIZATION';
}

function isLoopbackCallback(url: string): boolean {
    try {
        const host = new URL(url).hostname.toLowerCase();
        return host === 'localhost' || host === '127.0.0.1' || host === '::1' || host === '[::1]';
    } catch { return false; }
}


const PROVIDER_NAMES: Partial<Record<RemoteStorageProvider, string>> = {
    GITHUB: 'GitHub', GITLAB: 'GitLab', ONEDRIVE: 'Microsoft / OneDrive', GOOGLE_DRIVE: 'Google Drive', DROPBOX: 'Dropbox',
};

interface ProviderRegistrationGuide {
    where: string;
    idLabel: string;
    idHelp: string;
    idExample: string;
    steps: string[];
    callbackNote: string;
}

/**
 * Registration happens once per HYDRA installation. End users never need to
 * create their own provider application and never paste passwords, MFA codes
 * or client secrets into HYDRA.
 */
const PROVIDER_GUIDES: Partial<Record<RemoteStorageProvider, ProviderRegistrationGuide>> = {
    GITHUB: {
        where: 'GitHub → Settings → Developer settings → OAuth Apps → New OAuth App',
        idLabel: 'Client ID',
        idHelp: 'Copy the value labelled Client ID exactly. It is a public alphanumeric application identifier. Do not paste Client secret.',
        idExample: 'Iv1.0123456789abcdef',
        steps: [
            'Create one OAuth App for this HYDRA installation. For normal browser use, keep Device Flow disabled and register the exact callback shown by HYDRA.',
            'Copy Client ID into HYDRA. Store the matching Client secret only in HYDRA_GITHUB_OAUTH_CLIENT_SECRET on the server; never paste it into the browser UI.',
            'HYDRA uses authorization code with PKCE and state validation. Enable Device Flow only for headless or constrained deployments where a callback cannot be used.',
        ],
        callbackNote: 'For normal browser authorization, register the callback URL exactly, keep GitHub callback wildcard matching disabled, and configure the matching Client secret only on the HYDRA server. Device Flow needs only the Client ID but should be reserved for headless or constrained deployments.',
    },
    GITLAB: {
        where: 'GitLab → Preferences / User Settings → Applications → Add new application',
        idLabel: 'Application ID',
        idHelp: 'Copy Application ID exactly. It is the public identifier of the GitLab application. Do not paste Secret.',
        idExample: '0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef',
        steps: [
            'Create one application for this HYDRA installation and select only the scopes HYDRA needs. For a PKCE/public-client registration, do not require confidential client authentication.',
            'Copy Application ID into HYDRA. Users do not register their own applications.',
            'HYDRA uses browser authorization with PKCE by default. Device Authorization is intended for constrained/headless use and requires a GitLab version that supports that grant.',
        ],
        callbackNote: 'GitLab performs password, passkey or 2FA on its own page. Register the callback URL exactly for browser authorization; self-managed GitLab must expose the matching OAuth endpoints.',
    },
    ONEDRIVE: {
        where: 'Microsoft Entra admin center → App registrations → New registration',
        idLabel: 'Application (client) ID',
        idHelp: 'Copy Application (client) ID from the app Overview page. It is a GUID. Do not paste Object ID, Directory ID or a client secret.',
        idExample: '00000000-0000-0000-0000-000000000000',
        steps: [
            'Register HYDRA once and note the Supported account types selected in the Entra application.',
            'If the app is limited to this organization, copy both Application (client) ID and the organization directory ID from Overview. For applications that accept several organizations or personal accounts, choose the matching account type in HYDRA instead.',
            'Use the browser authorization-code flow with PKCE for normal deployments. If HYDRA is hosted on HTTPS, configure the generated Client secret only on the HYDRA server. Enable public client/device flows only for local, desktop or headless deployments that need them.',
        ],
        callbackNote: 'Local loopback authorization can use a public client with PKCE. Hosted HTTPS deployments should register the exact callback as a Web redirect and keep the Client secret only on the HYDRA server. Device sign-in is an explicit fallback for constrained/headless environments.',
    },
    GOOGLE_DRIVE: {
        where: 'Google Cloud Console → APIs & Services → Credentials → Create credentials → OAuth client ID',
        idLabel: 'Client ID',
        idHelp: 'Copy Client ID exactly. Google client IDs normally end in .apps.googleusercontent.com. Do not paste Client secret.',
        idExample: '123456789012-abc123.apps.googleusercontent.com',
        steps: [
            'Configure the Google consent screen, enable the Google Drive API and create one OAuth client for this HYDRA installation. Local HYDRA uses a Desktop app client; hosted HYDRA uses a Web application client.',
            'Copy Client ID into HYDRA. Local loopback mode uses PKCE and does not need a client secret. Hosted Web application mode registers the exact callback URL and keeps the matching secret only on the HYDRA server.',
            'HYDRA uses PKCE; Google performs account login, 2-Step Verification and consent on Google pages.',
        ],
        callbackNote: 'Local HYDRA uses the loopback callback with a Desktop app OAuth client and PKCE. Hosted deployments use a Web application OAuth client, an exact HTTPS callback and a server-side client secret.',
    },
    DROPBOX: {
        where: 'Dropbox App Console → Create app → Settings',
        idLabel: 'App key',
        idHelp: 'Copy App key exactly. Dropbox uses this public value as the OAuth client ID. Do not paste App secret.',
        idExample: 'abc123def456ghi',
        steps: [
            'Create one scoped-access Dropbox app for this HYDRA installation. End users must not create their own Dropbox app.',
            'Copy App key into HYDRA and add the callback URL below to Redirect URIs.',
            'Choose App Folder access unless the connection truly needs pre-existing files outside HYDRA’s app folder, and enable only the Dropbox scopes HYDRA needs. HYDRA uses authorization code with PKCE and requests offline access so refresh tokens can keep the connection usable without storing the user password.',
        ],
        callbackNote: 'Dropbox performs account login and security challenges before returning authorization to HYDRA. Add the callback URL exactly to Redirect URIs; PKCE does not require the App secret in this browser setup.',
    },
};

const PROVIDER_REGISTRATION_URLS: Partial<Record<RemoteStorageProvider, string>> = {
    GITHUB: 'https://github.com/settings/developers',
    GITLAB: 'https://gitlab.com/-/profile/applications',
    ONEDRIVE: 'https://entra.microsoft.com/#view/Microsoft_AAD_RegisteredApps/ApplicationsListBlade',
    GOOGLE_DRIVE: 'https://console.cloud.google.com/apis/credentials',
    DROPBOX: 'https://www.dropbox.com/developers/apps',
};

/** Public provider-application setup. Never accepts or returns client secrets. */
export function OAuthProviderSetup({ provider, onChanged }: Props) {
    const { t } = useHydraLanguage();
    const [setup, setSetup] = useState<RemoteOAuthProviderSetupDescriptor | null>(null);
    const [registrationKit, setRegistrationKit] = useState<RemoteOAuthRegistrationKit | null>(null);
    const [clientId, setClientId] = useState('');
    const [tenant, setTenant] = useState('common');
    const [microsoftMode, setMicrosoftMode] = useState<MicrosoftAccountMode>('COMMON');
    const [scopeText, setScopeText] = useState('');
    const [deviceFlowPreferred, setDeviceFlowPreferred] = useState(false);
    const [busy, setBusy] = useState(false);
    const [message, setMessage] = useState('');
    const supported = provider !== 'GENERIC_GIT';
    const providerName = PROVIDER_NAMES[provider] ?? provider;
    const guide = PROVIDER_GUIDES[provider];

    const load = useCallback(async () => {
        if (!supported) { setSetup(null); return; }
        const [items, kit] = await Promise.all([loadRemoteOAuthProviderSetups(), loadRemoteOAuthRegistrationKit(provider)]);
        const next = items.find(item => item.provider === provider) ?? null;
        setSetup(next);
        setRegistrationKit(kit);
        setClientId(next?.clientId ?? '');
        const loadedTenant = next?.tenant || 'common';
        const loadedMode = microsoftAccountMode(loadedTenant);
        setMicrosoftMode(loadedMode);
        setTenant(loadedMode === 'ORGANIZATION' ? loadedTenant : loadedMode === 'ORGANIZATIONS' ? 'organizations' : loadedMode === 'CONSUMERS' ? 'consumers' : 'common');
        setScopeText((next?.scopes ?? []).join('\n'));
        setDeviceFlowPreferred(next?.deviceFlowPreferred ?? false);
    }, [provider, supported]);

    useEffect(() => {
        let active = true;
        void Promise.resolve().then(load).catch(error => {
            if (active) setMessage(t(error instanceof Error ? error.message : 'Could not load provider application setup.'));
        });
        return () => { active = false; };
    }, [load, t]);

    const scopes = useMemo(() => scopeText.split(/[\n,]+/).map(value => value.trim()).filter(Boolean), [scopeText]);
    const readyToSave = clientId.trim() !== '' && scopes.length > 0 && (provider !== 'ONEDRIVE' || tenant.trim() !== '');
    const supportsDeviceSignIn = provider === 'GITHUB' || provider === 'GITLAB' || provider === 'ONEDRIVE';
    const loopbackGoogle = provider === 'GOOGLE_DRIVE' && isLoopbackCallback(setup?.callbackUrl ?? '');
    const readinessNote = setup === null || setup.oauthReady ? ''
        : provider === 'GITHUB' && !deviceFlowPreferred && !setup.confidentialCredentialConfigured ? 'GitHub browser authorization needs HYDRA_GITHUB_OAUTH_CLIENT_SECRET on the HYDRA server. Alternatively enable Device Flow here and in the GitHub OAuth App.'
        : provider === 'GOOGLE_DRIVE' && !loopbackGoogle && !setup.confidentialCredentialConfigured ? 'Hosted Google Drive access uses HYDRA as a Web application OAuth client. Configure HYDRA_GOOGLE_OAUTH_CLIENT_SECRET on the HYDRA server and register the exact callback URL shown below.'
        : provider === 'ONEDRIVE' && !deviceFlowPreferred && !isLoopbackCallback(setup.callbackUrl) && !setup.confidentialCredentialConfigured ? 'Hosted Microsoft / OneDrive access uses a Web application registration. Configure HYDRA_MICROSOFT_OAUTH_CLIENT_SECRET only on the HYDRA server and register the exact callback URL shown below.'
        : 'The public application settings were saved, but this provider still needs an installation-level requirement before users can connect.';
    const microsoftOptions = [
        { value: 'ORGANIZATION', label: t('Only this organization'), description: t('Use the organization directory ID or verified organization domain shown on the Entra application Overview page.'), category: t('Microsoft account type') },
        { value: 'ORGANIZATIONS', label: t('Any work or school account'), description: t('Use only when the Entra application is configured to accept work or school accounts from several organizations.'), category: t('Microsoft account type') },
        { value: 'COMMON', label: t('Work, school and personal Microsoft accounts'), description: t('Use only when the Entra application accepts both organizational and personal Microsoft accounts.'), category: t('Microsoft account type') },
        { value: 'CONSUMERS', label: t('Personal Microsoft accounts only'), description: t('Use when the Entra application is configured for personal Microsoft accounts.'), category: t('Microsoft account type') },
    ];

    function changeMicrosoftMode(value: string): void {
        const next = value as MicrosoftAccountMode;
        setMicrosoftMode(next);
        if (next === 'ORGANIZATIONS') setTenant('organizations');
        else if (next === 'COMMON') setTenant('common');
        else if (next === 'CONSUMERS') setTenant('consumers');
        else if (['common', 'organizations', 'consumers'].includes(tenant.trim().toLowerCase())) setTenant('');
    }

    if (!supported) return <div className="oauth-provider-setup managed"><strong>{t('Server-managed credential')}</strong><span>{t('Generic Git does not use browser account connection. Configure its token in the server credential source and keep it out of this form.')}</span></div>;

    async function save(): Promise<void> {
        if (!readyToSave || setup === null || !setup.uiConfigurationAllowed) return;
        setBusy(true); setMessage('');
        try {
            const next = await saveRemoteOAuthProviderSetup(provider, { clientId: clientId.trim(), tenant: tenant.trim(), scopes, deviceFlowPreferred });
            setSetup(next); setMessage(t(next.oauthReady ? 'Provider application saved. Users can now press Connect and sign in on the provider page.' : 'Provider application saved, but one installation requirement is still missing. Review the setup note below.')); await onChanged?.();
        } catch (error) { setMessage(t(error instanceof Error ? error.message : 'Could not save provider application setup.')); }
        finally { setBusy(false); }
    }

    async function reset(): Promise<void> {
        if (setup === null || !setup.uiConfigurationAllowed) return;
        setBusy(true); setMessage('');
        try {
            const next = await resetRemoteOAuthProviderSetup(provider);
            const nextMode = microsoftAccountMode(next.tenant || 'common');
            setSetup(next); setClientId(next.clientId); setMicrosoftMode(nextMode); setTenant(next.tenant || (nextMode === 'ORGANIZATION' ? '' : 'common')); setScopeText(next.scopes.join('\n')); setDeviceFlowPreferred(next.deviceFlowPreferred);
            setMessage(t('Provider application setup restored to the server defaults.')); await onChanged?.();
        } catch (error) { setMessage(t(error instanceof Error ? error.message : 'Could not reset provider application setup.')); }
        finally { setBusy(false); }
    }

    function publicRegistrationText(): string {
        if (registrationKit === null) return '';
        const lines = [
            `HYDRA - ${registrationKit.displayName}`,
            `${t('Application name')}: ${registrationKit.applicationNameSuggestion}`,
            `${t('Application type')}: ${registrationKit.applicationType}`,
            `${t('Provider administration')}: ${registrationKit.administrationUrl}`,
            `${t('PKCE')}: ${registrationKit.pkceRequired ? t('Required') : t('Not required')}`,
            `${t('Scopes')}: ${registrationKit.scopes.join(' ')}`,
        ];
        if (registrationKit.callbackMustBeRegistered) lines.push(`${t('Callback URL to register exactly')}: ${registrationKit.callbackUrl}`);
        else lines.push(`${t('Callback used by HYDRA')}: ${registrationKit.callbackUrl}`);
        if (registrationKit.serverCredentialRequired) lines.push(`${t('Server secret variable')}: ${registrationKit.serverCredentialEnvironmentVariable}`);
        lines.push(t('This sheet contains no password, access token, refresh token or client secret.'));
        return lines.join('\n');
    }

    async function copyRegistrationKit(): Promise<void> {
        if (registrationKit === null) return;
        try {
            await navigator.clipboard.writeText(publicRegistrationText());
            setMessage(t('Public registration data copied. No secret was included.'));
        } catch {
            setMessage(t('The browser could not copy the registration data. Use the download button instead.'));
        }
    }

    function downloadRegistrationKit(): void {
        if (registrationKit === null) return;
        const publicDocument = {
            schema: 'hydra.oauth-registration-kit',
            provider: registrationKit.provider,
            displayName: registrationKit.displayName,
            applicationNameSuggestion: registrationKit.applicationNameSuggestion,
            applicationType: registrationKit.applicationType,
            administrationUrl: registrationKit.administrationUrl,
            clientIdLabel: registrationKit.clientIdLabel,
            callbackUrl: registrationKit.callbackUrl,
            callbackMustBeRegistered: registrationKit.callbackMustBeRegistered,
            pkceRequired: registrationKit.pkceRequired,
            deviceFlowSupported: registrationKit.deviceFlowSupported,
            deviceFlowPreferred: registrationKit.deviceFlowPreferred,
            scopes: registrationKit.scopes,
            serverCredentialRequired: registrationKit.serverCredentialRequired,
            serverCredentialEnvironmentVariable: registrationKit.serverCredentialEnvironmentVariable,
            containsSecrets: false,
        };
        const blob = new Blob([JSON.stringify(publicDocument, null, 2)], { type: 'application/json' });
        const url = URL.createObjectURL(blob);
        const anchor = document.createElement('a');
        anchor.href = url;
        anchor.download = `hydra-${provider.toLowerCase().replaceAll('_', '-')}-registration.json`;
        document.body.appendChild(anchor);
        anchor.click();
        anchor.remove();
        URL.revokeObjectURL(url);
        setMessage(t('Public registration sheet downloaded. It contains no secret values.'));
    }

    return <section className="oauth-provider-setup" aria-label={`${providerName} ${t('provider application setup')}`}>
        <header>
            <div><strong>{providerName} · {t('Installation setup')}</strong><span>{t('This is a one-time administrator task. Normal users never need this screen: they only press Connect and sign in on the provider page.')}</span></div>
            <span className={`oauth-provider-status ${setup?.oauthReady ? 'ready' : 'incomplete'}`}>{setup?.oauthReady ? t('Ready for users') : t('Setup needed once')}</span>
        </header>

        <div className="oauth-provider-registration-simple">
            <strong>{provider === 'ONEDRIVE' ? t('Microsoft application registration') : t('Only one public application ID is required here')}</strong>
            <ol>
                <li>{t('Open the provider administration page using the button below.')}</li>
                <li>{t(guide?.where ?? 'Create or open the application registered for this HYDRA installation.')}</li>
                <li>{provider === 'ONEDRIVE' ? t('Copy Application (client) ID and select the same account type configured in Entra. If the app is limited to one organization, also copy its organization directory ID.') : t('Copy the public application identifier shown by the provider into the field below and save.')}</li>
            </ol>
            {(registrationKit?.administrationUrl ?? PROVIDER_REGISTRATION_URLS[provider]) !== undefined && <a href={registrationKit?.administrationUrl ?? PROVIDER_REGISTRATION_URLS[provider]} target="_blank" rel="noreferrer">{t('Open provider administration')}</a>}
        </div>

        {registrationKit !== null && <section className="oauth-registration-kit" aria-label={t('HYDRA registration data')}>
            <header><div><strong>{t('What HYDRA provides for registration')}</strong><span>{t('These are public deployment values that can be copied into the provider console. HYDRA never puts passwords, tokens or client-secret values in this sheet.')}</span></div><div><HydraButton size="small" type="button" onClick={() => void copyRegistrationKit()}>{t('Copy registration data')}</HydraButton><HydraButton size="small" type="button" onClick={downloadRegistrationKit}>{t('Download public JSON')}</HydraButton></div></header>
            <dl>
                <div><dt>{t('Application name')}</dt><dd><code>{registrationKit.applicationNameSuggestion}</code></dd></div>
                <div><dt>{t('Application type')}</dt><dd>{registrationKit.applicationType}</dd></div>
                <div><dt>{t('Public identifier to bring back to HYDRA')}</dt><dd>{registrationKit.clientIdLabel}</dd></div>
                <div><dt>{t('Requested permissions')}</dt><dd><code>{registrationKit.scopes.join(' ')}</code></dd></div>
                <div className="oauth-registration-kit-wide"><dt>{t(registrationKit.callbackMustBeRegistered ? 'Provider callback registration' : 'Callback used by HYDRA')}</dt><dd><code>{registrationKit.callbackUrl}</code><small>{t(registrationKit.callbackMustBeRegistered ? 'Register this exact value in the provider. HYDRA validates the callback server-side and does not accept arbitrary redirect targets.' : 'This flow does not require you to register this callback manually. HYDRA still keeps the callback fixed and validates it server-side.')}</small></dd></div>
                {registrationKit.serverCredentialRequired && <div className="oauth-registration-kit-wide"><dt>{t('Server-only secret')}</dt><dd><code>{registrationKit.serverCredentialEnvironmentVariable}</code><small>{t('Create the client secret in the provider, but put its value only in this HYDRA server environment variable. It is never accepted by this browser form, returned by the API or included in downloads.')}</small></dd></div>}
            </dl>
        </section>}

        <div className="oauth-provider-safety-note"><strong>{t('Safe by design')}</strong><span>{t('Do not enter a password, MFA code, Authenticator code or application secret here. HYDRA only accepts the provider public application identifier; account sign-in happens on the provider page.')}</span></div>

        {setup !== null && !setup.uiConfigurationAllowed && <div className="oauth-provider-policy"><strong>{t('Managed by the server')}</strong><span>{t('This installation does not allow provider application settings to be changed in the browser. Ask the HYDRA administrator to configure the provider on the server.')}</span></div>}
        {readinessNote !== '' && <div className="oauth-provider-policy"><strong>{t('One installation requirement remains')}</strong><span>{t(readinessNote)}</span></div>}
        {provider === 'GOOGLE_DRIVE' && setup !== null && <div className="oauth-provider-safety-note"><strong>{t(loopbackGoogle ? 'Google local / desktop mode' : 'Google hosted Web application mode')}</strong><span>{t(loopbackGoogle ? 'This HYDRA installation uses a local loopback callback. Create a Google OAuth client of type Desktop app, copy its Client ID here and let PKCE protect the authorization code. No client secret is required.' : 'This HYDRA installation uses a hosted callback. Use a Google OAuth client of type Web application, register the callback URL exactly and configure the client secret only on the HYDRA server.')}</span></div>}

        <div className="oauth-provider-grid oauth-provider-essential-grid">
            <HydraFormField label={t(guide?.idLabel ?? 'Application ID')} targetId={`oauth-client-id-${provider}`} required help={t(guide?.idHelp ?? 'Public application identifier supplied by the provider. It is not a password or secret.')} example={guide?.idExample}>
                <input id={`oauth-client-id-${provider}`} autoComplete="off" disabled={busy || setup?.uiConfigurationAllowed === false} placeholder={guide?.idExample} value={clientId} onChange={event => setClientId(event.target.value)} />
            </HydraFormField>
            {provider === 'ONEDRIVE' && <HydraFormField label={t('Microsoft account type')} targetId="oauth-microsoft-account-type" required help={t('This must match Supported account types in the Microsoft Entra application registration. A mismatch prevents Microsoft from opening the sign-in page.')}>
                <HydraSelect id="oauth-microsoft-account-type" ariaLabel={t('Microsoft account type')} showFilter={false} value={microsoftMode} options={microsoftOptions} onChange={changeMicrosoftMode} />
            </HydraFormField>}
            {provider === 'ONEDRIVE' && microsoftMode === 'ORGANIZATION' && <HydraFormField label={t('Organization identifier or domain')} targetId="oauth-microsoft-tenant" required help={t('Copy the organization directory ID from the Entra application Overview page, or use the verified organization domain such as contoso.onmicrosoft.com. Do not paste the Application (client) ID here.')} example="00000000-0000-0000-0000-000000000000">
                <input id="oauth-microsoft-tenant" autoComplete="off" disabled={busy || setup?.uiConfigurationAllowed === false} value={tenant} onChange={event => setTenant(event.target.value)} />
            </HydraFormField>}
            {setup !== null && (!supportsDeviceSignIn || !deviceFlowPreferred) && <HydraFormField className="oauth-provider-wide" label={t(registrationKit?.callbackMustBeRegistered === false ? 'Callback used by HYDRA' : 'Callback URL to register exactly')} targetId={`oauth-callback-${provider}`} help={t(registrationKit?.callbackMustBeRegistered === false ? 'This provider flow manages the callback without a manual redirect registration. Keep the displayed value unchanged; HYDRA validates it server-side.' : 'Copy this exact URL into the provider redirect/callback field. Do not change its scheme, host, port or path.')}>
                <input id={`oauth-callback-${provider}`} readOnly value={setup.callbackUrl} onFocus={event => event.currentTarget.select()} />
            </HydraFormField>}
        </div>

        {guide !== undefined && <details className="oauth-provider-guide"><summary>{t('Provider-specific instructions')}</summary><div><strong>{t(guide.where)}</strong><ol>{guide.steps.map(step => <li key={step}>{t(step)}</li>)}</ol><p>{t(guide.callbackNote)}</p></div></details>}

        <details className="oauth-provider-advanced"><summary>{t('Advanced installation settings')}</summary><p>{t('The defaults are the safest choice for most installations. Change these values only when the provider or your organization requires it.')}</p><div className="oauth-provider-advanced-grid">
            {supportsDeviceSignIn && <HydraFormField label={t('Sign-in method')} targetId={`oauth-device-flow-${provider}`} help={t('Use device sign-in only for desktop, headless or constrained deployments. The provider shows a short code and handles sign-in on its own page. For normal browser deployments, prefer authorization with PKCE when the provider supports it.')}>
                <HydraCheckbox id={`oauth-device-flow-${provider}`} checked={deviceFlowPreferred} disabled={busy || setup?.uiConfigurationAllowed === false} onChange={event => setDeviceFlowPreferred(event.target.checked)} label={t('Use provider device sign-in when available')} />
            </HydraFormField>}
            <HydraFormField className="oauth-provider-wide" label={t('Provider scopes')} targetId={`oauth-scopes-${provider}`} required help={t('One scope per line or separated by commas. These are technical provider permissions, not passwords.')}><textarea id={`oauth-scopes-${provider}`} disabled={busy || setup?.uiConfigurationAllowed === false} rows={5} value={scopeText} onChange={event => setScopeText(event.target.value)} /></HydraFormField>
        </div></details>

        <div className="oauth-provider-actions">
            <HydraButton disabled={busy || !readyToSave || setup?.uiConfigurationAllowed === false} variant="primary" onClick={() => void save()}>{busy ? t('Working…') : t('Save installation setup')}</HydraButton>
            {setup?.uiOverride && <HydraButton disabled={busy || setup.uiConfigurationAllowed === false} variant="quiet" onClick={() => void reset()}>{t('Use server defaults')}</HydraButton>}
        </div>
        {message !== '' && <p className="oauth-provider-message" role="status">{message}</p>}
    </section>;
}
