import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { OAuthProviderSetup } from './OAuthProviderSetup';

const load = vi.fn();
const save = vi.fn();
const reset = vi.fn();
const loadRegistrationKit = vi.fn();
vi.mock('./api', () => ({
    loadRemoteOAuthProviderSetups: (...args: unknown[]) => load(...args),
    loadRemoteOAuthRegistrationKit: (...args: unknown[]) => loadRegistrationKit(...args),
    saveRemoteOAuthProviderSetup: (...args: unknown[]) => save(...args),
    resetRemoteOAuthProviderSetup: (...args: unknown[]) => reset(...args),
}));

const registrationKit = { provider:'ONEDRIVE', displayName:'Microsoft / OneDrive', administrationUrl:'https://entra.microsoft.com/', applicationType:'Desktop/public client with PKCE', applicationNameSuggestion:'HYDRA - Microsoft / OneDrive', clientIdLabel:'Application (client) ID', callbackUrl:'http://127.0.0.1:8081/api/remote-auth/callback', callbackMustBeRegistered:true, pkceRequired:true, deviceFlowSupported:true, deviceFlowPreferred:false, serverCredentialRequired:false, serverCredentialEnvironmentVariable:'HYDRA_MICROSOFT_OAUTH_CLIENT_SECRET', scopes:['offline_access','Files.ReadWrite.AppFolder'] };

const descriptor = { provider: 'ONEDRIVE', clientId: '', tenant: 'common', scopes: ['offline_access','Files.ReadWrite.AppFolder'], deviceFlowPreferred: false, uiOverride: false, oauthReady: false, confidentialCredentialConfigured: false, uiConfigurationAllowed: true, callbackUrl: 'http://127.0.0.1:8081/api/remote-auth/callback' };

describe('guided OAuth provider setup', () => {
    beforeEach(() => { load.mockReset(); save.mockReset(); reset.mockReset(); loadRegistrationKit.mockReset(); load.mockResolvedValue([descriptor]); loadRegistrationKit.mockResolvedValue(registrationKit); });

    it('edits only public OAuth application coordinates and saves them', async () => {
        save.mockResolvedValue({ ...descriptor, clientId: 'client-123', oauthReady: true, uiOverride: true });
        render(<OAuthProviderSetup provider="ONEDRIVE" />);
        expect(await screen.findByText('Microsoft application registration')).toBeTruthy();
        expect(screen.getByText('Safe by design')).toBeTruthy();
        expect(screen.getByText('What HYDRA provides for registration')).toBeTruthy();
        expect(screen.getByText('HYDRA - Microsoft / OneDrive')).toBeTruthy();
        const clientId = screen.getByLabelText('Application (client) ID');
        fireEvent.change(clientId, { target: { value: 'client-123' } });
        fireEvent.click(screen.getByRole('button', { name: 'Save installation setup' }));
        await waitFor(() => expect(save).toHaveBeenCalledTimes(1));
        expect(save.mock.calls[0][0]).toBe('ONEDRIVE');
        expect(save.mock.calls[0][1]).toMatchObject({ clientId: 'client-123', tenant: 'common' });
        expect(JSON.stringify(save.mock.calls[0][1]).toLowerCase()).not.toContain('secret');
    });

    it('shows callback registration and keeps advanced scopes behind disclosure', async () => {
        render(<OAuthProviderSetup provider="ONEDRIVE" />);
        expect(await screen.findByText('Callback URL to register exactly')).toBeTruthy();
        expect(screen.getByText('Advanced installation settings')).toBeTruthy();
        expect(screen.queryByText('Client secret')).toBeNull();
    });
});
