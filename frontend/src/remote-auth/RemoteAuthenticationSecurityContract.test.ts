import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';

function source(path: string): string { return readFileSync(resolve(process.cwd(), path), 'utf8'); }

describe('HYDRA V4.28 external authentication security contract', () => {
    it('keeps OAuth credential material outside browser DTOs', () => {
        const types = source('src/remote-auth/types.ts');
        const api = source('src/remote-auth/api.ts');
        expect(types).not.toMatch(/accessToken|refreshToken|clientSecret/);
        expect(api).not.toMatch(/access_token|refresh_token|client_secret/);
        expect(api).toContain('X-Hydra-Request-Token');
        expect(types).not.toMatch(/deviceCode/);
    });

    it('reserves and navigates the provider window during the user gesture without handling secrets', () => {
        const settings = source('src/remote-auth/ExternalConnectionsSettings.tsx');
        const launcher = source('src/remote-auth/providerAuthorizationWindow.ts');
        expect(settings).toContain('reserveProviderAuthorizationWindow');
        expect(settings.indexOf('reserveProviderAuthorizationWindow')).toBeLessThan(settings.indexOf('await startRemoteAuthorization'));
        expect(settings).toContain('navigateProviderAuthorizationWindow(authorizationWindow, safeAuthorizationUrl)');
        expect(launcher).toContain('window.open(bridge.href');
        expect(source('public/remote-auth-bridge.js')).toContain('window.opener = null');
        expect(launcher).toContain("target.protocol !== 'https:'");
        expect(launcher).toContain('target.location.replace(safeUrl)');
        expect(settings).not.toMatch(/type=["']password|clientSecret|accessToken|refreshToken|deviceCode/);
        expect(settings).toContain('pollRemoteAuthorization');
    });

    it('protects every credential-backed remote mutation with the local request token', () => {
        const remote = source('src/remote-storage/api.ts');
        expect(remote.match(/remoteRequestHeaders/g)?.length ?? 0).toBeGreaterThanOrEqual(4);
    });
});
