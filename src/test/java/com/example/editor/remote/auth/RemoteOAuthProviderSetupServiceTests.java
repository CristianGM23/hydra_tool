package com.example.editor.remote.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.example.editor.remote.storage.RemoteStorageProviderType;
import tools.jackson.databind.json.JsonMapper;

class RemoteOAuthProviderSetupServiceTests {
    @TempDir Path temporary;

    @Test
    void persistsOnlyPublicOAuthCoordinatesAndReappliesThemOnRestart() throws Exception {
        Path file=temporary.resolve("provider-setups.json");
        RemoteAuthProperties firstProperties=properties(file,true);
        RemoteOAuthProviderSetupStore firstStore=new RemoteOAuthProviderSetupStore(firstProperties,JsonMapper.builder().findAndAddModules().build());
        RemoteOAuthProviderSetupService first=new RemoteOAuthProviderSetupService(firstProperties,firstStore);first.initialize();
        RemoteOAuthProviderSetupDescriptor saved=first.update(RemoteStorageProviderType.ONEDRIVE,new RemoteOAuthProviderSetupRequest("11111111-2222-4333-8444-555555555555","organizations",List.of("offline_access","Files.ReadWrite"),true));
        assertTrue(saved.oauthReady());assertTrue(saved.uiOverride());assertTrue(saved.deviceFlowPreferred());assertEquals("organizations",saved.tenant());
        String disk=Files.readString(file);
        assertTrue(disk.contains("11111111-2222-4333-8444-555555555555"));assertFalse(disk.toLowerCase().contains("clientsecret"));assertFalse(disk.contains("access_token"));

        RemoteAuthProperties restartedProperties=properties(file,true);
        RemoteOAuthProviderSetupService restarted=new RemoteOAuthProviderSetupService(restartedProperties,new RemoteOAuthProviderSetupStore(restartedProperties,JsonMapper.builder().findAndAddModules().build()));restarted.initialize();
        assertEquals("11111111-2222-4333-8444-555555555555",restartedProperties.provider(RemoteStorageProviderType.ONEDRIVE).getClientId());
        assertEquals("organizations",restartedProperties.provider(RemoteStorageProviderType.ONEDRIVE).getTenant());
    }

    @Test
    void gitLabDeviceSignInCanBeSelectedByTheAdministrativeSetup() {
        RemoteAuthProperties properties=properties(temporary.resolve("gitlab.json"),true);
        RemoteOAuthProviderSetupService service=new RemoteOAuthProviderSetupService(properties,new RemoteOAuthProviderSetupStore(properties,JsonMapper.builder().findAndAddModules().build()));service.initialize();
        RemoteOAuthProviderSetupDescriptor saved=service.update(RemoteStorageProviderType.GITLAB,new RemoteOAuthProviderSetupRequest("public-gitlab-application-id","",List.of("api"),true));
        assertTrue(saved.deviceFlowPreferred());
        assertTrue(properties.provider(RemoteStorageProviderType.GITLAB).isDeviceFlowPreferred());
    }


    @Test
    void rejectsMicrosoftClientIdAndTenantMixupsBeforeContactingMicrosoft() {
        RemoteAuthProperties properties=properties(temporary.resolve("microsoft-validation.json"),true);
        RemoteOAuthProviderSetupService service=new RemoteOAuthProviderSetupService(properties,new RemoteOAuthProviderSetupStore(properties,JsonMapper.builder().findAndAddModules().build()));service.initialize();
        assertThrows(RemoteAuthException.class,()->service.update(RemoteStorageProviderType.ONEDRIVE,new RemoteOAuthProviderSetupRequest("not-a-guid","common",List.of("offline_access","Files.ReadWrite"),true)));
        String appId="11111111-2222-4333-8444-555555555555";
        assertThrows(RemoteAuthException.class,()->service.update(RemoteStorageProviderType.ONEDRIVE,new RemoteOAuthProviderSetupRequest(appId,appId,List.of("offline_access","Files.ReadWrite"),true)));
    }


    @Test
    void providerReadinessMatchesTheActualFlowRequirements() {
        RemoteAuthProperties properties=properties(temporary.resolve("readiness.json"),true);
        RemoteOAuthProviderSetupService service=new RemoteOAuthProviderSetupService(properties,new RemoteOAuthProviderSetupStore(properties,JsonMapper.builder().findAndAddModules().build()));service.initialize();

        RemoteOAuthProviderSetupDescriptor githubDevice=service.update(RemoteStorageProviderType.GITHUB,new RemoteOAuthProviderSetupRequest("Iv1.0123456789abcdef","",List.of("repo"),true));
        assertTrue(githubDevice.oauthReady());
        RemoteOAuthProviderSetupDescriptor githubBrowser=service.update(RemoteStorageProviderType.GITHUB,new RemoteOAuthProviderSetupRequest("Iv1.0123456789abcdef","",List.of("repo"),false));
        assertFalse(githubBrowser.oauthReady());

        RemoteOAuthProviderSetupDescriptor googleLocal=service.update(RemoteStorageProviderType.GOOGLE_DRIVE,new RemoteOAuthProviderSetupRequest("123456789012-abc123.apps.googleusercontent.com","",List.of("openid"),false));
        assertTrue(googleLocal.oauthReady());
        properties.setCallbackBaseUrl("https://hydra.example");
        RemoteOAuthProviderSetupDescriptor googleHostedWithoutSecret=service.list().stream().filter(item->item.provider()==RemoteStorageProviderType.GOOGLE_DRIVE).findFirst().orElseThrow();
        assertFalse(googleHostedWithoutSecret.oauthReady());
        properties.provider(RemoteStorageProviderType.GOOGLE_DRIVE).setClientSecret("server-side-secret");
        RemoteOAuthProviderSetupDescriptor googleWithSecret=service.list().stream().filter(item->item.provider()==RemoteStorageProviderType.GOOGLE_DRIVE).findFirst().orElseThrow();
        assertTrue(googleWithSecret.oauthReady());
    }

    @Test
    void generatesSecretFreeRegistrationMaterialForProviderConsoles() {
        RemoteAuthProperties properties=properties(temporary.resolve("registration-kit.json"),true);
        RemoteOAuthProviderSetupService service=new RemoteOAuthProviderSetupService(properties,new RemoteOAuthProviderSetupStore(properties,JsonMapper.builder().findAndAddModules().build()));service.initialize();
        RemoteOAuthRegistrationKit github=service.registrationKit(RemoteStorageProviderType.GITHUB);
        assertEquals("GitHub",github.displayName());assertTrue(github.administrationUrl().startsWith("https://github.com/"));
        assertTrue(github.pkceRequired());assertTrue(github.deviceFlowSupported());assertFalse(github.deviceFlowPreferred());
        assertTrue(github.callbackMustBeRegistered());assertTrue(github.serverCredentialRequired());
        assertEquals("HYDRA_GITHUB_OAUTH_CLIENT_SECRET",github.serverCredentialEnvironmentVariable());
        assertTrue(github.scopes().contains("repo"));


        RemoteOAuthRegistrationKit microsoftLocal=service.registrationKit(RemoteStorageProviderType.ONEDRIVE);
        assertEquals("Desktop/public client with PKCE",microsoftLocal.applicationType());assertTrue(microsoftLocal.callbackMustBeRegistered());assertFalse(microsoftLocal.serverCredentialRequired());
        assertTrue(microsoftLocal.scopes().contains("Files.ReadWrite.AppFolder"));

        RemoteOAuthRegistrationKit googleLocal=service.registrationKit(RemoteStorageProviderType.GOOGLE_DRIVE);
        assertEquals("Desktop app OAuth client",googleLocal.applicationType());assertFalse(googleLocal.callbackMustBeRegistered());assertFalse(googleLocal.serverCredentialRequired());
        properties.setCallbackBaseUrl("https://hydra.example");

        RemoteOAuthRegistrationKit microsoftHosted=service.registrationKit(RemoteStorageProviderType.ONEDRIVE);
        assertEquals("Web application / confidential server client",microsoftHosted.applicationType());assertTrue(microsoftHosted.callbackMustBeRegistered());assertTrue(microsoftHosted.serverCredentialRequired());
        assertEquals("HYDRA_MICROSOFT_OAUTH_CLIENT_SECRET",microsoftHosted.serverCredentialEnvironmentVariable());

        RemoteOAuthRegistrationKit googleHosted=service.registrationKit(RemoteStorageProviderType.GOOGLE_DRIVE);
        assertEquals("Web application OAuth client",googleHosted.applicationType());assertTrue(googleHosted.callbackMustBeRegistered());assertTrue(googleHosted.serverCredentialRequired());
        assertEquals("HYDRA_GOOGLE_OAUTH_CLIENT_SECRET",googleHosted.serverCredentialEnvironmentVariable());
    }

    @Test
    void productionStylePolicyCanDisableUiProviderSetup() {
        RemoteAuthProperties properties=properties(temporary.resolve("disabled.json"),false);
        RemoteOAuthProviderSetupService service=new RemoteOAuthProviderSetupService(properties,new RemoteOAuthProviderSetupStore(properties,JsonMapper.builder().findAndAddModules().build()));service.initialize();
        assertFalse(service.list().stream().filter(item->item.provider()==RemoteStorageProviderType.GITHUB).findFirst().orElseThrow().uiConfigurationAllowed());
        assertThrows(RemoteAuthException.class,()->service.update(RemoteStorageProviderType.GITHUB,new RemoteOAuthProviderSetupRequest("client", "common",List.of("repo"),true)));
    }

    @Test
    void rejectsSecretsControlCharactersAndUnsupportedProviders() {
        RemoteAuthProperties properties=properties(temporary.resolve("validation.json"),true);
        RemoteOAuthProviderSetupService service=new RemoteOAuthProviderSetupService(properties,new RemoteOAuthProviderSetupStore(properties,JsonMapper.builder().findAndAddModules().build()));service.initialize();
        assertThrows(RemoteAuthException.class,()->service.update(RemoteStorageProviderType.GITHUB,new RemoteOAuthProviderSetupRequest("bad\nclient","common",List.of("repo"),true)));
        assertThrows(RemoteAuthException.class,()->service.update(RemoteStorageProviderType.GENERIC_GIT,new RemoteOAuthProviderSetupRequest("client","common",List.of("repo"),false)));
    }

    private RemoteAuthProperties properties(Path file,boolean allowUi){RemoteAuthProperties properties=new RemoteAuthProperties();properties.setProviderSetupFile(file);properties.setAllowUiProviderSetup(allowUi);return properties;}
}
