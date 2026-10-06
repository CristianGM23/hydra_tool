const PROVIDER_WINDOW_PREFIX = 'hydra-external-provider-signin';
const PROVIDER_BRIDGE_READY = 'HYDRA_REMOTE_AUTH_READY';
const PROVIDER_BRIDGE_NAVIGATE = 'HYDRA_REMOTE_AUTH_NAVIGATE';

export interface ProviderAuthorizationWindow {
    target: Window | null;
    channel: BroadcastChannel | null;
    ready: boolean;
    pendingUrl: string;
    fallbackTimer: number | null;
}

/**
 * Reserves a same-origin bridge synchronously during the original user click.
 * The bridge severs opener before HYDRA later crosses to the external provider.
 */
export function reserveProviderAuthorizationWindow(title: string, message: string, language: 'en' | 'es' = 'en'): ProviderAuthorizationWindow {
    const nonce = authorizationNonce();
    const channelName = `hydra-remote-auth-${nonce}`;
    const basePath = `${import.meta.env.BASE_URL || '/'}${(import.meta.env.BASE_URL || '/').endsWith('/') ? '' : '/'}`;
    const bridge = new URL(`${basePath}remote-auth-bridge.html`, window.location.origin);
    bridge.searchParams.set('lang', language);
    bridge.hash = encodeURIComponent(channelName);
    const target = window.open(bridge.href, `${PROVIDER_WINDOW_PREFIX}-${nonce}`, 'popup=yes,width=760,height=820,resizable=yes,scrollbars=yes');
    const launch: ProviderAuthorizationWindow = { target, channel:null, ready:false, pendingUrl:'', fallbackTimer:null };
    if (target === null || typeof BroadcastChannel === 'undefined') return launch;
    const channel = new BroadcastChannel(channelName);
    launch.channel = channel;
    channel.onmessage = event => {
        if (event.data?.type !== PROVIDER_BRIDGE_READY) return;
        launch.ready = true;
        if (launch.pendingUrl !== '') channel.postMessage({ type:PROVIDER_BRIDGE_NAVIGATE, url:launch.pendingUrl });
    };
    void title; void message;
    return launch;
}

/** Accept only HTTPS destinations without embedded credentials. */
export function providerAuthorizationUrl(value: string): string {
    const target = new URL(value);
    if (target.protocol !== 'https:' || target.username !== '' || target.password !== '') throw new Error('The provider returned an invalid authorization URL.');
    return target.href;
}

/** Navigate the already user-opened bridge after the backend creates the OAuth request. */
export function navigateProviderAuthorizationWindow(launch: ProviderAuthorizationWindow, authorizationUrl: string): boolean {
    const safeUrl = providerAuthorizationUrl(authorizationUrl);
    const target = launch.target;
    if (target === null || target.closed) return false;
    launch.pendingUrl = safeUrl;
    if (launch.channel !== null) {
        if (launch.ready) launch.channel.postMessage({ type:PROVIDER_BRIDGE_NAVIGATE, url:safeUrl });
        if (launch.fallbackTimer !== null) window.clearTimeout(launch.fallbackTimer);
        launch.fallbackTimer = window.setTimeout(() => {
            try { if (!target.closed) { target.location.replace(safeUrl); target.focus(); } } catch { /* Safe fallback link remains visible in HYDRA. */ }
        }, 1400);
        try { target.focus(); } catch { /* best effort */ }
        return true;
    }
    try { target.location.replace(safeUrl); target.focus(); return true; } catch { return false; }
}

export function closeProviderAuthorizationWindow(launch: ProviderAuthorizationWindow): void {
    if (launch.fallbackTimer !== null) window.clearTimeout(launch.fallbackTimer);
    try { launch.channel?.close(); } catch { /* best effort */ }
    try { if (launch.target !== null && !launch.target.closed) launch.target.close(); } catch { /* best effort */ }
}

function authorizationNonce(): string {
    if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') return crypto.randomUUID();
    const random = new Uint32Array(4);
    if (typeof crypto !== 'undefined' && typeof crypto.getRandomValues === 'function') crypto.getRandomValues(random);
    else for (let index=0; index<random.length; index++) random[index] = Math.floor(Math.random()*0xffffffff);
    return Array.from(random, value => value.toString(16).padStart(8,'0')).join('');
}
