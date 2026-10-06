import type { RemoteStorageProvider } from '../remote-storage/types';

export interface RemoteAuthProfileDescriptor {
    profileId: string;
    displayName: string;
    provider: RemoteStorageProvider;
    connectionId: string;
    oauthConfigured: boolean;
    connected: boolean;
    accountLabel: string;
    scopes: string[];
    expiresAt: string | null;
    persistent: boolean;
    vaultReady: boolean;
    protectionMode: string;
}

export interface RemoteAuthStartResponse {
    profileId: string;
    connectionId: string;
    state: string;
    authorizationUrl: string;
    userCode: string;
    deviceFlow: boolean;
    pollIntervalSeconds: number;
    expiresAt: string;
}

export interface RemoteAuthConnectionDescriptor {
    connectionId: string;
    provider: RemoteStorageProvider;
    accountId: string;
    accountLabel: string;
    scopes: string[];
    expiresAt: string | null;
    refreshable: boolean;
    persistent: boolean;
    protectionMode: string;
}

export interface RemoteAuthPollResponse {
    state: string;
    profileId: string;
    completed: boolean;
    pending: boolean;
    pollAfterSeconds: number;
    connection: RemoteAuthConnectionDescriptor | null;
}

export interface RemoteRequestSecurityContext { requestToken: string; }

export interface RemoteOAuthProviderSetupDescriptor {
    provider: RemoteStorageProvider;
    clientId: string;
    tenant: string;
    scopes: string[];
    deviceFlowPreferred: boolean;
    uiOverride: boolean;
    oauthReady: boolean;
    confidentialCredentialConfigured: boolean;
    uiConfigurationAllowed: boolean;
    callbackUrl: string;
}

export interface RemoteOAuthProviderSetupRequest {
    clientId: string;
    tenant: string;
    scopes: string[];
    deviceFlowPreferred: boolean;
}

export interface RemoteOAuthRegistrationKit {
    provider: RemoteStorageProvider;
    displayName: string;
    administrationUrl: string;
    applicationType: string;
    applicationNameSuggestion: string;
    clientIdLabel: string;
    callbackUrl: string;
    callbackMustBeRegistered: boolean;
    pkceRequired: boolean;
    deviceFlowSupported: boolean;
    deviceFlowPreferred: boolean;
    serverCredentialRequired: boolean;
    serverCredentialEnvironmentVariable: string;
    scopes: string[];
}
