import { formatActionableError } from '../api/actionableError';
import { explainInputError } from '../api/inputErrorExplanation';
import { testRemoteStorageConnection } from '../remote-storage/api';
import type { RemoteStorageConnectionTestResult, RemoteStorageProfileConfiguration, RemoteStorageProvider } from '../remote-storage/types';
import { providerCompletionSummary, providerVisibleName } from './repositoryProfileModel';

export type RepositoryConnectionTestState = 'IDLE' | 'RUNNING' | 'SUCCESS' | 'FAILED';

/** Runs the non-destructive backend connectivity probe and converts it into user-facing state. */
export async function executeRepositoryConnectionTest(profile: RemoteStorageProfileConfiguration, t: (value: string) => string): Promise<{ state: RepositoryConnectionTestState; message: string }> {
    try {
        const result = await testRemoteStorageConnection(profile.id);
        return result.connected
            ? { state: 'SUCCESS', message: t('Repository connection verified successfully. HYDRA reached the configured destination without changing remote content.') }
            : { state: 'FAILED', message: explainRepositoryTestFailure(result, profile, t) };
    } catch (error) {
        return { state: 'FAILED', message: explainInputError(error, t('Repository connection test failed.'), 'repository connection') };
    }
}

function explainRepositoryTestFailure(result: RemoteStorageConnectionTestResult, profile: RemoteStorageProfileConfiguration, t: (value: string) => string): string {
    const provider = result.provider ?? profile.provider;
    const providerName = providerVisibleName(provider, t);
    const target = providerCompletionSummary(profile, t);
    const technical = result.message?.trim() || t('The provider did not return additional technical detail.');
    const lower = technical.toLowerCase();
    const status = httpStatus(technical);
    const field = providerTargetField(provider);

    if (!result.credentialsConfigured && provider !== 'GENERIC_GIT') {
        return formatActionableError(
            t('The external account is not connected for this repository profile.'),
            'account',
            t('HYDRA has the destination coordinates, but it does not have a usable authorization for the selected provider.'),
            t('Open the Access step of this connection, press Connect, complete sign-in and MFA on the provider page, return to HYDRA and run Test connection again.'),
            [t('HYDRA never needs the provider password in this form.'), `${t('Destination')}: ${target}.`]
        );
    }
    if (containsAny(lower, ['unknownhost', 'unknown host', 'name or service not known', 'temporary failure in name resolution', 'dns'])) {
        return formatActionableError(
            t('HYDRA cannot resolve the server name used by this connection.'),
            'providerUrl',
            t('The HYDRA backend cannot translate the configured host name into a reachable network address.'),
            t('Verify the destination URL and provider host. If they are correct, test DNS from the machine running HYDRA and then check VPN, proxy and corporate network settings. Retry only after the server can resolve the host.'),
            [`${t('Provider')}: ${providerName}.`, `${t('Destination')}: ${target}.`, `${t('Technical detail')}: ${technical}`]
        );
    }
    if (containsAny(lower, ['timed out', 'timeout', 'read timed out', 'connect timed out'])) {
        return formatActionableError(
            t('The external service did not answer before the connection timeout.'),
            'providerUrl',
            t('HYDRA reached the connection phase but did not receive a response within the configured time.'),
            t('Open the same provider from the HYDRA server network, verify VPN/proxy/firewall access and the configured provider URL, then run Test connection again. Do not change OAuth credentials solely because of a network timeout.'),
            [`${t('Provider')}: ${providerName}.`, `${t('Destination')}: ${target}.`, `${t('Technical detail')}: ${technical}`]
        );
    }
    if (containsAny(lower, ['connection refused', 'connectexception', 'failed to connect'])) {
        return formatActionableError(
            t('The remote server was reached, but it refused the connection.'),
            'providerUrl',
            t('No service accepted the connection on the configured host and port, or a firewall/reverse proxy rejected it.'),
            t('Verify the exact HTTPS host and port, confirm the external service is running, and check firewall, reverse-proxy and container port mappings from the HYDRA server. Then retry Test connection.'),
            [`${t('Provider')}: ${providerName}.`, `${t('Technical detail')}: ${technical}`]
        );
    }
    if (containsAny(lower, ['pkix', 'sslhandshake', 'unable to find valid certification path']) || (lower.includes('certificate') && lower.includes('trust'))) {
        return formatActionableError(
            t('HYDRA cannot establish a trusted HTTPS connection with the remote service.'),
            'providerUrl',
            t('The certificate presented by the server is expired, does not match the host, or its certification chain is not trusted by the Java runtime used by HYDRA.'),
            t('Check the certificate hostname, expiry and intermediate certificates. For an internal certification authority, install the CA certificate in the trust store used by the HYDRA Java runtime and restart HYDRA. Do not disable TLS certificate verification.'),
            [`${t('Provider')}: ${providerName}.`, `${t('Technical detail')}: ${technical}`]
        );
    }
    if (status === 401) {
        return formatActionableError(
            t('The provider rejected the current authorization.'),
            'account',
            t('The stored authorization is missing, expired, revoked or no longer belongs to the application used by this connection.'),
            t('Open Access, disconnect the current account if it is still shown, connect it again and complete sign-in/MFA on the provider page. Then run Test connection without changing the destination URL.'),
            [`${t('Provider')}: ${providerName}.`, `${t('Destination')}: ${target}.`]
        );
    }
    if (status === 403) {
        return formatActionableError(
            t('The connected account does not have permission to use this destination.'),
            'permissions',
            t('The provider accepted the account but denied this repository, project, drive, folder or operation.'),
            providerPermissionFix(provider, t),
            [`${t('Destination')}: ${target}.`, t('First verify that the same account can open the destination in the provider web interface.'), `${t('Technical detail')}: ${technical}`]
        );
    }
    if (status === 404) {
        return formatActionableError(
            t('The configured repository, project, branch, drive or folder could not be found.'),
            field,
            t('The destination saved in HYDRA no longer resolves for the connected account, or one of its derived coordinates is incorrect.'),
            providerNotFoundFix(provider, t),
            [`${t('Current destination')}: ${target}.`, t('Check capitalization, branch names and copied folder or repository URLs.'), `${t('Technical detail')}: ${technical}`]
        );
    }
    if (status === 429) {
        return formatActionableError(
            t('The provider is temporarily limiting this connection.'),
            'providerUrl',
            t('The provider rate limit or API quota has been reached for this account or application.'),
            t('Wait for the provider retry window, avoid repeated connection tests, and try again. If the limit is reached frequently, review the provider API quota or application rate limits.'),
            [`${t('Provider')}: ${providerName}.`, `${t('Technical detail')}: ${technical}`]
        );
    }
    if (status >= 500) {
        return formatActionableError(
            t('The provider returned a server-side error while HYDRA tested the connection.'),
            'providerUrl',
            t('The destination was contacted, but the external service could not complete the request.'),
            t('Keep the HYDRA profile unchanged, check the provider service status and retry later. If the provider reports no incident, verify network/proxy access from the HYDRA server before changing OAuth configuration.'),
            [`${t('Provider')}: ${providerName}.`, `${t('Destination')}: ${target}.`, `${t('Technical detail')}: ${technical}`]
        );
    }
    if (status === 400) {
        return formatActionableError(
            t('The provider rejected one of the saved connection coordinates.'),
            field,
            t('The request reached the provider, but a repository/project identifier, branch, folder, API parameter or authorization coordinate is not valid for this profile.'),
            t('Return to Paste URL, paste the destination URL again and press Recognize destination. Save the newly derived coordinates. If the error persists after recognition, review the provider application setup and reconnect the account only if the diagnostic points to authorization.'),
            [`${t('Current destination')}: ${target}.`, `${t('Technical detail')}: ${technical}`]
        );
    }
    return formatActionableError(
        `${t('HYDRA could not verify the connection to')} ${providerName}.`,
        field,
        `${t('The provider test failed, but its response does not contain a recognizable HTTP or authorization cause. HYDRA therefore cannot safely label it as a password, permission, missing-resource or network error.')} ${t('The exact provider response is kept below instead of guessing.')}`,
        t('Verify the connection in this order: paste the complete URL again and press Recognize destination; confirm the detected provider and destination; make sure Access shows the account as connected when required; then run Test connection again. If it still fails, use the technical detail to check the provider service, network or server log before changing OAuth credentials.'),
        [`${t('Provider')}: ${providerName}.`, `${t('Destination')}: ${target}.`, `${t('Technical detail')}: ${technical}`]
    );
}

function httpStatus(message: string): number {
    const value = message.match(/\bHTTP\s+(\d{3})\b/i)?.[1];
    return value === undefined ? -1 : Number.parseInt(value, 10);
}
function containsAny(value: string, markers: string[]): boolean { return markers.some(marker => value.includes(marker)); }
function providerTargetField(provider: RemoteStorageProvider): string {
    if (provider === 'GITHUB') return 'repository/branch';
    if (provider === 'GITLAB') return 'projectId/branch';
    if (provider === 'ONEDRIVE' || provider === 'GOOGLE_DRIVE') return 'drive/folder';
    if (provider === 'DROPBOX') return 'rootPath';
    return 'repositoryUrl';
}
function providerPermissionFix(provider: RemoteStorageProvider, t: (value: string) => string): string {
    if (provider === 'GITHUB') return t('Verify repository access for the connected GitHub account, organization SSO authorization and the OAuth app permissions. Reconnect after changing organization or application permissions.');
    if (provider === 'GITLAB') return t('Verify project membership and the API/repository scopes granted to the GitLab application. For self-managed GitLab, also review project or group access policy, then reconnect.');
    if (provider === 'ONEDRIVE') return t('Verify that the account can open the target OneDrive or SharePoint location and that the Microsoft application has the required Graph file permissions and administrator consent when required. Then reconnect.');
    if (provider === 'GOOGLE_DRIVE') return t('Verify that Google Drive API is enabled, that the account can open the target folder and that the requested Drive scopes are allowed by the OAuth consent screen and Workspace policy. Then reconnect.');
    if (provider === 'DROPBOX') return t('Verify the Dropbox app Permissions tab and that the connected account can open the target path. Submit permission changes and reconnect so the new authorization includes them.');
    return t('Verify the remote Git account or server credentials and confirm that the repository allows the requested read operation.');
}
function providerNotFoundFix(provider: RemoteStorageProvider, t: (value: string) => string): string {
    if (provider === 'GITHUB') return t('Paste the GitHub repository URL again, press Recognize destination and verify owner, repository and branch. For private repositories, confirm the connected account can open that repository.');
    if (provider === 'GITLAB') return t('Paste the GitLab project URL again, press Recognize destination and verify the complete namespace/project path and branch. For self-managed GitLab, also verify the server base URL.');
    if (provider === 'ONEDRIVE') return t('Paste the OneDrive or SharePoint folder URL again, press Recognize destination and verify the item still exists and is shared with the connected account.');
    if (provider === 'GOOGLE_DRIVE') return t('Paste the Google Drive folder or document URL again, press Recognize destination and verify the item has not been moved or deleted and is accessible to the connected account.');
    if (provider === 'DROPBOX') return t('Paste the Dropbox shared URL or path again, press Recognize destination and verify the item is still available to the connected account.');
    return t('Verify the HTTPS Git repository URL, branch and credentials, then save the profile and test again.');
}
