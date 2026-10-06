import { useCallback, useEffect, useRef, useState } from 'react';
import { HydraActionableErrorNotice, HydraButton } from '../design-system';
import { explainInputError, isActionableErrorMessage } from '../api/inputErrorExplanation';
import { useHydraLanguage } from '../i18n';
import { disconnectRemoteAuthorization, loadRemoteAuthProfiles, pollRemoteAuthorization, RemoteAuthApiError, startRemoteAuthorization } from './api';
import type { RemoteAuthProfileDescriptor } from './types';
import type { RemoteStorageProvider } from '../remote-storage/types';
import { OAuthProviderSetup } from './OAuthProviderSetup';
import { closeProviderAuthorizationWindow, navigateProviderAuthorizationWindow, providerAuthorizationUrl, reserveProviderAuthorizationWindow, type ProviderAuthorizationWindow } from './providerAuthorizationWindow';
import './external-connections.css';

const CALLBACK_POLL_INTERVAL_MS = 1200;
const MAXIMUM_AUTHORIZATION_MILLISECONDS = 15 * 60 * 1000;

interface Props {
    provider?: RemoteStorageProvider;
    profileId?: string;
    showProviderSetup?: boolean;
    autoConnect?: boolean;
    reservedAuthorizationWindow?: ProviderAuthorizationWindow | null;
    onAutoConnectHandled?: () => void;
    onReservedAuthorizationWindowConsumed?: () => void;
    onStateChange?: (profile: RemoteAuthProfileDescriptor | null) => void;
}

function CableConnectionIcon({ connected = false }: { connected?: boolean }) {
    return <svg aria-hidden="true" className={`external-cable-icon${connected ? ' connected' : ''}`} viewBox="0 0 36 36" fill="none"><path d="M5 10h8v6H9v4c0 4 3 7 7 7h4"/><path d="M31 26h-8v-6h4v-4c0-4-3-7-7-7h-4"/><path d="M13 12h10M13 14h10"/></svg>;
}

/**
 * Conexión de cuentas externas orientada al usuario.
 * HYDRA inicia el flujo; el proveedor abre su propia pantalla de acceso y
 * solicita allí contraseña, MFA o consentimiento. El navegador de HYDRA nunca
 * recibe esas credenciales.
 */
export function ExternalConnectionsSettings({ provider, profileId, showProviderSetup = false, autoConnect = false, reservedAuthorizationWindow = null, onAutoConnectHandled, onReservedAuthorizationWindowConsumed, onStateChange }: Props = {}) {
    const { t, language } = useHydraLanguage();
    const [profiles, setProfiles] = useState<RemoteAuthProfileDescriptor[]>([]);
    const [busy, setBusy] = useState('');
    const [message, setMessage] = useState('');
    const [showSetup, setShowSetup] = useState(false);
    const [authorizationLink, setAuthorizationLink] = useState('');
    const alive = useRef(true);
    const autoConnectAttempted = useRef(false);
    const visibleProfiles = profiles.filter(profile => (provider === undefined || profile.provider === provider) && (profileId === undefined || profile.profileId === profileId));

    const refresh = useCallback(async () => {
        try {
            const loaded = await loadRemoteAuthProfiles();
            if (alive.current) {
                setProfiles(loaded);
                const matching = loaded.find(item => (provider === undefined || item.provider === provider) && (profileId === undefined || item.profileId === profileId)) ?? null;
                onStateChange?.(matching);
            }
            return loaded;
        } catch (error) {
            if (alive.current) setMessage(explainInputError(error, t('Could not load external connections.'), 'external connection'));
            return [];
        }
    }, [onStateChange, profileId, provider, t]);

    const waitForDeviceAuthorization = useCallback(async (state: string, initialIntervalSeconds: number): Promise<void> => {
        let elapsedMilliseconds = 0;
        let intervalSeconds = Math.max(1, initialIntervalSeconds);
        while (alive.current && elapsedMilliseconds < MAXIMUM_AUTHORIZATION_MILLISECONDS) {
            const waitMilliseconds = intervalSeconds * 1000;
            await delay(waitMilliseconds);
            elapsedMilliseconds += waitMilliseconds;
            const result = await pollRemoteAuthorization(state);
            if (result.completed) return;
            intervalSeconds = Math.max(1, result.pollAfterSeconds);
        }
        throw new Error(t('Authorization is still pending. You can refresh the connection status later.'));
    }, [t]);

    const waitForCallbackAuthorization = useCallback(async (state: string): Promise<void> => {
        let elapsedMilliseconds = 0;
        let intervalMilliseconds = CALLBACK_POLL_INTERVAL_MS;
        while (alive.current && elapsedMilliseconds < MAXIMUM_AUTHORIZATION_MILLISECONDS) {
            await delay(intervalMilliseconds);
            elapsedMilliseconds += intervalMilliseconds;
            const result = await pollRemoteAuthorization(state);
            if (result.completed) return;
            intervalMilliseconds = Math.max(CALLBACK_POLL_INTERVAL_MS, result.pollAfterSeconds * 1000);
        }
        throw new Error(t('Authorization is still pending. You can refresh the connection status later.'));
    }, [t]);

    useEffect(() => {
        alive.current = true;
        void Promise.resolve().then(refresh);
        return () => { alive.current = false; };
    }, [refresh]);

    const connect = useCallback(async (profile: RemoteAuthProfileDescriptor, preReservedWindow: ProviderAuthorizationWindow | null = null): Promise<void> => {
        if (!profile.vaultReady) {
            if (preReservedWindow !== null) { closeProviderAuthorizationWindow(preReservedWindow); onReservedAuthorizationWindowConsumed?.(); }
            setMessage(t('Secure credential storage is not available in this HYDRA installation. Desktop mode can initialize an owner-protected local vault; managed servers must configure the vault master key.'));
            return;
        }
        if (!profile.oauthConfigured) {
            if (preReservedWindow !== null) { closeProviderAuthorizationWindow(preReservedWindow); onReservedAuthorizationWindowConsumed?.(); }
            setShowSetup(true);
            setMessage(t('This provider is not available in this HYDRA installation yet. If you administer HYDRA, open Installation setup below and configure it once. Other users only need Connect after that.'));
            return;
        }
        // Reserve the external window synchronously inside the user's click. Opening it only
        // after the backend round-trip is commonly blocked because browser user activation has expired.
        const authorizationWindow = preReservedWindow ?? reserveProviderAuthorizationWindow(t('Connect an external account'), t('Opening the provider sign-in…'), language);
        if (preReservedWindow !== null) onReservedAuthorizationWindowConsumed?.();
        setAuthorizationLink('');
        setBusy(profile.profileId); setMessage(t('Opening the provider sign-in…'));
        try {
            const started = await startRemoteAuthorization(profile.profileId, language);
            const safeAuthorizationUrl = providerAuthorizationUrl(started.authorizationUrl);
            setAuthorizationLink(safeAuthorizationUrl);
            const opened = navigateProviderAuthorizationWindow(authorizationWindow, safeAuthorizationUrl);
            if (!opened) setMessage(t('The browser blocked the automatic provider window. Use “Open provider sign-in” below; the authorization request is ready.'));
            if (started.deviceFlow) {
                setMessage(`${t('The provider will ask you to confirm this temporary code:')} ${started.userCode}`);
                await waitForDeviceAuthorization(started.state, started.pollIntervalSeconds);
            } else {
                setMessage(t('Complete sign-in in the provider window. HYDRA will detect the authorization automatically.'));
                await waitForCallbackAuthorization(started.state);
            }
            if (alive.current) { await refresh(); setAuthorizationLink(''); setMessage(t('Connection authorized. HYDRA can now use this destination; your sign-in credentials stayed with the provider.')); }
        } catch (error) {
            closeProviderAuthorizationWindow(authorizationWindow);
            const errorMessage = explainInputError(error, t('External authorization failed.'), 'external connection');
            if (alive.current) {
                if (error instanceof RemoteAuthApiError && error.code === 'PROVIDER_NOT_CONFIGURED') setShowSetup(true);
                setMessage(errorMessage);
            }
        } finally {
            if (alive.current) setBusy('');
        }
    }, [language, onReservedAuthorizationWindowConsumed, refresh, t, waitForCallbackAuthorization, waitForDeviceAuthorization]);

    useEffect(() => {
        if (!autoConnect) { autoConnectAttempted.current = false; return undefined; }
        if (autoConnectAttempted.current || visibleProfiles.length !== 1) return undefined;
        const profile = visibleProfiles[0];
        autoConnectAttempted.current = true;
        onAutoConnectHandled?.();
        let active = true;
        if (!profile.connected) queueMicrotask(() => { if (active) void connect(profile, reservedAuthorizationWindow); });
        return () => { active = false; };
    }, [autoConnect, connect, onAutoConnectHandled, reservedAuthorizationWindow, visibleProfiles]);

    async function disconnect(profile: RemoteAuthProfileDescriptor): Promise<void> {
        setBusy(profile.profileId); setMessage('');
        try { await disconnectRemoteAuthorization(profile.profileId); await refresh(); setMessage(t('External connection disconnected.')); }
        catch (error) { setMessage(explainInputError(error, t('Could not disconnect the external connection.'), 'external connection')); }
        finally { if (alive.current) setBusy(''); }
    }


    return <div className="external-connections-settings">
        {provider === undefined && <div className="external-connections-intro"><CableConnectionIcon /><div><strong>{t('Connect an external account')}</strong><span>{t('Press Connect. GitHub, GitLab, Microsoft, Google or Dropbox opens its own sign-in screen and asks for the account, password, MFA or consent it requires. HYDRA never asks for or sees that password.')}</span></div></div>}
        {profileId !== undefined && visibleProfiles.length === 0 && <div className="external-connection-waiting"><CableConnectionIcon /><div><strong>{t('Save this connection first')}</strong><span>{t('Once the destination is saved, HYDRA can open the provider sign-in directly from this step.')}</span></div></div>}
        {visibleProfiles.map(profile => <article className={`external-connection-card${profile.connected ? ' connected' : ''}`} key={profile.profileId}>
            <div className="external-connection-main"><span aria-label={profile.connected ? t('Connected') : t('Not connected yet')} className={`external-connection-state${profile.connected ? ' connected' : ''}`} role="img" /><CableConnectionIcon connected={profile.connected} /><div><strong>{profile.displayName}</strong><small>{profile.provider === 'GENERIC_GIT' ? t('Server-managed Git access') : profile.connected ? (profile.accountLabel || t('Connected')) : profile.oauthConfigured ? t('Ready to connect your account') : t('Waiting for installation setup')}</small></div></div>
            <dl><div><dt>{t('Access')}</dt><dd>{profile.connected ? t('Authorized by the provider') : t('Not connected yet')}</dd></div><div><dt>{t('Credential storage')}</dt><dd>{profile.persistent ? t('Encrypted on this HYDRA server') : t('Memory only')}</dd></div>{profile.expiresAt !== null && <div><dt>{t('Expires')}</dt><dd>{new Date(profile.expiresAt).toLocaleString()}</dd></div>}</dl>
            <div className="external-connection-actions">
                {profile.provider === 'GENERIC_GIT' ? <span className="external-connection-managed-note">{t('Generic Git uses a credential managed by the HYDRA server.')}</span> : !profile.connected
                    ? <><HydraButton disabled={busy !== ''} loading={busy === profile.profileId} variant="primary" onClick={() => void connect(profile)}>{t('Connect')}</HydraButton>{!profile.vaultReady && <span className="external-connection-blocker">{t('Secure credential storage must be initialized before connecting an account.')}</span>}{profile.vaultReady && !profile.oauthConfigured && <span className="external-connection-blocker">{t('This provider must be enabled once for this HYDRA installation before accounts can connect.')}</span>}</>
                    : <><HydraButton disabled={busy !== ''} onClick={() => void connect(profile)}>{t('Change account')}</HydraButton><HydraButton disabled={busy !== ''} loading={busy === profile.profileId} variant="danger" onClick={() => void disconnect(profile)}>{t('Disconnect')}</HydraButton></>}
            </div>
        </article>)}
        {(showProviderSetup || showSetup) && provider !== undefined && provider !== 'GENERIC_GIT' && <details className="external-provider-setup-disclosure" open={showSetup} onToggle={event => setShowSetup((event.currentTarget as HTMLDetailsElement).open)}><summary>{t('Installation setup (administrators only)')}</summary><p>{t('Normal users can ignore this section. It is used once to register this HYDRA installation with the provider; after that, account access is always started with Connect.')}</p><OAuthProviderSetup provider={provider} onChanged={async () => { await refresh(); setShowSetup(false); }} /></details>}
        {authorizationLink !== '' && <div className="external-provider-signin-recovery"><div><strong>{t('Provider page ready')}</strong><span>{t('If the provider window did not open automatically, use this safe fallback link.')}</span></div><a className="external-provider-signin-link" href={authorizationLink} target="_blank" rel="noopener noreferrer">{t('Open provider sign-in')}</a></div>}
        {message !== '' && isActionableErrorMessage(message) ? <HydraActionableErrorNotice className="external-connections-message" message={message} context="external connection" onDismiss={() => setMessage('')} /> : message !== '' ? <p className="external-connections-message" role="status">{message}</p> : null}
    </div>;
}



function delay(milliseconds: number): Promise<void> { return new Promise(resolve => window.setTimeout(resolve, milliseconds)); }
