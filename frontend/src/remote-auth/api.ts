import { backendFetch } from '../api/backendFetch';
import { readActionableApiError } from '../api/actionableError';
import type { RemoteAuthPollResponse, RemoteAuthProfileDescriptor, RemoteAuthStartResponse, RemoteRequestSecurityContext } from './types';

let requestTokenPromise: Promise<string> | null = null;

export class RemoteAuthApiError extends Error {
    readonly code: string;
    readonly status: number;
    readonly technicalDetail: string;
    constructor(code: string, status: number, message: string, technicalDetail = '') { super(message); this.name = 'RemoteAuthApiError'; this.code = code; this.status = status; this.technicalDetail = technicalDetail; }
}

async function json<T>(response: Response): Promise<T> {
    if (!response.ok) {
        const error = await readActionableApiError(response);
        throw new RemoteAuthApiError(error.code, error.status, error.message, error.technicalDetail.trim() || error.message);
    }
    return response.json() as Promise<T>;
}

/** Obtiene el token efímero anti-CSRF de la instancia local y lo mantiene solo en memoria. */
export function remoteRequestToken(): Promise<string> {
    if (requestTokenPromise !== null) return requestTokenPromise;
    requestTokenPromise = backendFetch('/api/remote-auth/security-context', { cache: 'no-store' })
        .then(response => json<RemoteRequestSecurityContext>(response))
        .then(context => context.requestToken)
        .catch(error => { requestTokenPromise = null; throw error; });
    return requestTokenPromise;
}

export async function remoteRequestHeaders(extra: HeadersInit = {}): Promise<Headers> {
    const headers = new Headers(extra);
    headers.set('X-Hydra-Request-Token', await remoteRequestToken());
    return headers;
}

export async function loadRemoteAuthProfiles(): Promise<RemoteAuthProfileDescriptor[]> {
    return json<RemoteAuthProfileDescriptor[]>(await backendFetch('/api/remote-auth/profiles', { cache: 'no-store' }));
}

export async function startRemoteAuthorization(profileId: string, language: 'en' | 'es'): Promise<RemoteAuthStartResponse> {
    return json<RemoteAuthStartResponse>(await backendFetch('/api/remote-auth/start', {
        method: 'POST',
        headers: await remoteRequestHeaders({ 'Content-Type': 'application/json' }),
        body: JSON.stringify({ profileId, language }),
    }));
}

export async function pollRemoteAuthorization(state: string): Promise<RemoteAuthPollResponse> {
    return json<RemoteAuthPollResponse>(await backendFetch('/api/remote-auth/device-poll', {
        method: 'POST',
        headers: await remoteRequestHeaders({ 'Content-Type': 'application/json' }),
        body: JSON.stringify({ state }),
    }));
}

export async function disconnectRemoteAuthorization(profileId: string): Promise<void> {
    const response = await backendFetch(`/api/remote-auth/connection?profileId=${encodeURIComponent(profileId)}`, {
        method: 'DELETE', headers: await remoteRequestHeaders(),
    });
    if (!response.ok) throw await readActionableApiError(response);
}

export async function loadRemoteOAuthProviderSetups(): Promise<import('./types').RemoteOAuthProviderSetupDescriptor[]> {
    return json<import('./types').RemoteOAuthProviderSetupDescriptor[]>(await backendFetch('/api/remote-auth/provider-setups', { cache: 'no-store' }));
}

export async function saveRemoteOAuthProviderSetup(provider: import('../remote-storage/types').RemoteStorageProvider, request: import('./types').RemoteOAuthProviderSetupRequest): Promise<import('./types').RemoteOAuthProviderSetupDescriptor> {
    return json<import('./types').RemoteOAuthProviderSetupDescriptor>(await backendFetch(`/api/remote-auth/provider-setups/${encodeURIComponent(provider)}`, {
        method: 'PUT', headers: await remoteRequestHeaders({ 'Content-Type': 'application/json' }), body: JSON.stringify(request),
    }));
}

export async function resetRemoteOAuthProviderSetup(provider: import('../remote-storage/types').RemoteStorageProvider): Promise<import('./types').RemoteOAuthProviderSetupDescriptor> {
    return json<import('./types').RemoteOAuthProviderSetupDescriptor>(await backendFetch(`/api/remote-auth/provider-setups/${encodeURIComponent(provider)}`, {
        method: 'DELETE', headers: await remoteRequestHeaders(),
    }));
}

export async function loadRemoteOAuthRegistrationKit(provider: import('../remote-storage/types').RemoteStorageProvider): Promise<import('./types').RemoteOAuthRegistrationKit> {
    return json<import('./types').RemoteOAuthRegistrationKit>(await backendFetch(`/api/remote-auth/provider-setups/${encodeURIComponent(provider)}/registration-kit`, { cache: 'no-store' }));
}
