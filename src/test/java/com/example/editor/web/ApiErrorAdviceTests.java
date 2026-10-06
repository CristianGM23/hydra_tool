package com.example.editor.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ApiErrorAdviceTests {
    @Test
    void requiredFieldIdentifiesExactFieldAndConcreteCorrection() {
        ApiErrorAdvice.Details details = ApiErrorAdvice.from("INVALID_REQUEST", "name must not be blank", "name");
        assertEquals("name", details.field());
        assertTrue(details.reason().contains("mandatory value"));
        assertTrue(details.solution().contains("Complete the indicated field"));
    }

    @Test
    void microsoftMissingOrganizationUsesHighLevelTerminologyAndExactEntraPath() {
        ApiErrorAdvice.Details details = ApiErrorAdvice.from(
                "TOKEN_EXCHANGE_FAILED",
                "Microsoft OneDrive device authorization failed with HTTP 400: AADSTS50059: No tenant-identifying information found");
        assertEquals("oauth-microsoft-organization", details.field());
        assertTrue(details.reason().contains("Microsoft organization"));
        assertTrue(details.solution().contains("Entra > App registrations > your app > Overview"));
        assertTrue(details.solution().contains("organization directory ID"));
        String visible = (details.reason()+" "+details.solution()+" "+String.join(" ", details.suggestions())).toLowerCase();
        assertFalse(visible.contains("tenant"));
    }

    @Test
    void githubDeviceFlowExplainsExactlyWhereToEnableIt() {
        ApiErrorAdvice.Details details = ApiErrorAdvice.from(
                "PROVIDER_NOT_CONFIGURED",
                "GitHub Device Flow is disabled: device_flow_disabled");
        assertEquals("deviceFlow", details.field());
        assertTrue(details.solution().contains("GitHub > Settings > Developer settings > OAuth Apps"));
        assertTrue(details.solution().contains("enable Device Flow"));
        assertTrue(details.solution().contains("Connect account"));
    }

    @Test
    void gitlabInvalidClientExplainsPublicPkceVersusConfidentialRegistration() {
        ApiErrorAdvice.Details details = ApiErrorAdvice.from(
                "TOKEN_EXCHANGE_FAILED",
                "GitLab authorization failed with HTTP 400: invalid_client");
        assertEquals("clientId", details.field());
        assertTrue(details.solution().contains("GitLab > Applications"));
        assertTrue(details.solution().contains("PKCE public-client flow"));
        assertTrue(details.solution().contains("HYDRA server"));
    }

    @Test
    void googleRedirectMismatchNamesClientTypeAndExactCallbackRegistration() {
        ApiErrorAdvice.Details details = ApiErrorAdvice.from(
                "TOKEN_EXCHANGE_FAILED",
                "Google Drive authorization failed with HTTP 400: redirect_uri_mismatch");
        assertEquals("callbackUrl", details.field());
        assertTrue(details.solution().contains("Desktop OAuth client"));
        assertTrue(details.solution().contains("Web application OAuth client"));
        assertTrue(details.solution().contains("Authorized redirect URIs"));
        assertTrue(details.solution().contains("exact callback URL"));
    }

    @Test
    void dropboxPermissionFailureNamesPermissionsTabAndReconnectRequirement() {
        ApiErrorAdvice.Details details = ApiErrorAdvice.from(
                "REMOTE_ERROR",
                "Dropbox remote request failed with HTTP 403");
        assertEquals("permissions", details.field());
        assertTrue(details.solution().contains("Dropbox app Permissions tab"));
        assertTrue(details.solution().contains("reconnect"));
    }

    @Test
    void selfManagedGitlabDnsFailureDoesNotSuggestChangingCredentials() {
        ApiErrorAdvice.Details details = ApiErrorAdvice.from(
                "REMOTE_ERROR",
                "GitLab remote request failed: UnknownHostException gitlab.internal.example");
        assertEquals("providerUrl", details.field());
        assertTrue(details.solution().contains("DNS resolution"));
        assertTrue(details.solution().contains("VPN/proxy"));
        assertFalse(details.solution().toLowerCase().contains("client id"));
    }

    @Test
    void tlsFailureRequiresTrustStoreFixInsteadOfDisablingCertificateVerification() {
        ApiErrorAdvice.Details details = ApiErrorAdvice.from(
                "REMOTE_ERROR",
                "GitLab SSLHandshakeException PKIX unable to find valid certification path");
        assertTrue(details.solution().contains("trust store"));
        assertTrue(details.solution().contains("Do not disable TLS certificate verification"));
    }

    @Test
    void remoteNotFoundPointsToProviderSpecificCoordinates() {
        ApiErrorAdvice.Details details = ApiErrorAdvice.from(
                "REMOTE_ERROR",
                "GitHub repository operation failed with HTTP 404");
        assertEquals("repository/branch", details.field());
        assertTrue(details.solution().contains("owner, repository name and branch"));
        assertTrue(details.solution().contains("GitHub URL"));
    }
    @Test
    void genericRemoteErrorKeepsCauseSolutionAndChecksDifferentAndActionable() {
        ApiErrorAdvice.Details details = ApiErrorAdvice.from(
                "REMOTE_ERROR",
                "Remote endpoint rejected the connection");
        assertEquals("remoteLocation", details.field());
        assertTrue(details.reason().contains("does not identify a more specific"));
        assertTrue(details.solution().contains("Recognize destination"));
        assertTrue(details.solution().contains("Test connection"));
        assertFalse(details.reason().equals(details.solution()));
        assertFalse(details.suggestions().isEmpty());
    }

    @Test
    void incompleteRepositoryUrlPointsToTheUrlFieldAndConcreteRecognitionStep() {
        ApiErrorAdvice.Details details = ApiErrorAdvice.from(
                "INVALID_REQUEST",
                "Remote repository or folder URL must contain a host");
        assertEquals("repositoryUrl", details.field());
        assertTrue(details.reason().contains("incomplete or malformed"));
        assertTrue(details.solution().contains("https://github.com/organization/repository"));
        assertTrue(details.solution().contains("Recognize destination"));
    }

    @Test
    void genericMappingFailureKeepsConcreteBindingAndGivesOrderedRepair() {
        ApiErrorAdvice.Details details = ApiErrorAdvice.from("INVALID_REQUEST", "binding id=personName has unknown target feature 'fullName'", "$.rules[0].bindings[0]");
        assertTrue(details.reason().contains("mapping or correspondence"));
        assertTrue(details.reason().contains("fullName"));
        assertTrue(details.solution().contains("source type"));
        assertTrue(details.solution().contains("target feature"));
    }

    @Test
    void unknownFallbackPreservesConcreteBackendDetailInsteadOfGenericTemplate() {
        ApiErrorAdvice.Details details = ApiErrorAdvice.from("INVALID_REQUEST", "Custom invariant failed for node id=Order42", "node");
        assertTrue(details.reason().contains("Order42"));
        assertTrue(details.reason().contains("Custom invariant failed"));
        assertTrue(details.solution().contains("exact field, element or path"));
    }

}
