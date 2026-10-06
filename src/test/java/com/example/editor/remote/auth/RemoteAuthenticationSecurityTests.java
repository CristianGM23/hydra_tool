package com.example.editor.remote.auth;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.example.editor.remote.storage.RemoteStorageProviderType;
import tools.jackson.databind.json.JsonMapper;

class RemoteAuthenticationSecurityTests {
    @TempDir Path temporary;

    @Test
    void networkPolicyAllowsHttpsAndOnlyLiteralLoopbackHttp() {
        assertEquals("https",RemoteNetworkPolicy.requireSecure(URI.create("https://api.github.com/user"),true).getScheme());
        assertEquals("127.0.0.1",RemoteNetworkPolicy.requireSecure(URI.create("http://127.0.0.1:8081/api/remote-auth/callback"),true).getHost());
        assertThrows(RemoteAuthException.class,()->RemoteNetworkPolicy.requireSecure(URI.create("http://example.org/token"),true));
        assertThrows(RemoteAuthException.class,()->RemoteNetworkPolicy.requireSecure(URI.create("http://localhost:8081/callback"),true));
        assertThrows(RemoteAuthException.class,()->RemoteNetworkPolicy.requireSecure(URI.create("https://user:secret@example.org/token"),true));
    }

    @Test
    void pendingStatesAreBoundedExpiredAndSingleUse() {
        RemoteAuthProperties properties=new RemoteAuthProperties();properties.setMaximumPendingFlows(2);
        RemoteAuthFlowStore store=new RemoteAuthFlowStore(properties);
        store.put(flow("one",Instant.now().plusSeconds(30)));
        store.put(flow("two",Instant.now().plusSeconds(40)));
        store.put(flow("three",Instant.now().plusSeconds(50)));
        assertEquals(2,store.size());
        assertThrows(RemoteAuthException.class,()->store.consume("one"));
        assertEquals("two",store.consume("two").state());
        assertThrows(RemoteAuthException.class,()->store.consume("two"));
        store.put(flow("expired",Instant.now().minusSeconds(1)));
        assertThrows(RemoteAuthException.class,()->store.consume("expired"));
    }

    @Test
    void persistentVaultEncryptsSecretsAndRejectsTampering() throws Exception {
        RemoteAuthProperties properties=new RemoteAuthProperties();
        properties.getVault().setRoot(temporary.resolve("vault"));
        properties.getVault().setAllowProtectedLocalMasterKey(true);
        var mapper=JsonMapper.builder().findAndAddModules().build();
        EncryptedFileRemoteCredentialVault vault=new EncryptedFileRemoteCredentialVault(properties,mapper);
        RemoteCredentialMaterial material=new RemoteCredentialMaterial(RemoteStorageProviderType.GITHUB,"access-super-secret","refresh-super-secret","Bearer",Instant.now().plusSeconds(3600),List.of("repo"),"42","tester",Instant.now());
        vault.store("github-oauth",material);
        Path credential=temporary.resolve("vault/github-oauth.cred");
        byte[] encrypted=Files.readAllBytes(credential);
        String disk=new String(encrypted,java.nio.charset.StandardCharsets.ISO_8859_1);
        assertFalse(disk.contains("access-super-secret"));
        assertFalse(disk.contains("refresh-super-secret"));
        assertEquals("access-super-secret",vault.load("github-oauth").orElseThrow().accessToken());
        encrypted[encrypted.length-1]^=0x01;Files.write(credential,encrypted);
        assertThrows(RemoteAuthException.class,()->vault.load("github-oauth"));
    }

    @Test
    void browserRequestGuardRejectsCrossSiteAndMissingTokens() {
        RemoteRequestGuard guard=new RemoteRequestGuard();String token=guard.context().requestToken();
        guard.verify(token,"same-origin");
        assertThrows(RemoteAuthException.class,()->guard.verify("wrong","same-origin"));
        assertThrows(RemoteAuthException.class,()->guard.verify(token,"cross-site"));
    }

    @Test
    void publicDtosCannotContainOAuthSecrets() {
        for(Class<?> type:List.of(RemoteAuthConnectionDescriptor.class,RemoteAuthProfileDescriptor.class,RemoteAuthStartRequest.class,RemoteAuthStartResponse.class,RemoteAuthPollResponse.class,RemoteRequestSecurityContext.class,RemoteOAuthProviderSetupDescriptor.class,RemoteOAuthProviderSetupRequest.class,RemoteOAuthRegistrationKit.class)) {
            List<String> names=Arrays.stream(type.getRecordComponents()).map(java.lang.reflect.RecordComponent::getName).map(String::toLowerCase).toList();
            assertFalse(names.contains("accesstoken"));assertFalse(names.contains("refreshtoken"));assertFalse(names.contains("clientsecret"));assertFalse(names.contains("devicecode"));
        }
        assertTrue(Arrays.stream(RemoteRequestSecurityContext.class.getRecordComponents()).anyMatch(component->component.getName().equals("requestToken")));
    }

    @Test
    void githubPrefersAuthorizationCodePkceAndDoesNotShipAClientSecret() {
        RemoteAuthProperties properties=new RemoteAuthProperties();
        assertFalse(properties.provider(RemoteStorageProviderType.GITHUB).isDeviceFlowPreferred());
        assertTrue(properties.provider(RemoteStorageProviderType.GITHUB).getClientSecret().isBlank());
        assertEquals("https://github.com/login/device/code",properties.provider(RemoteStorageProviderType.GITHUB).getDeviceAuthorizationUrl());
    }

    @Test
    void gitLabUsesPkceByDefaultAndKeepsDeviceAuthorizationOptionalWithoutAClientSecret() {
        RemoteAuthProperties properties=new RemoteAuthProperties();
        var gitlab=properties.provider(RemoteStorageProviderType.GITLAB);
        assertFalse(gitlab.isDeviceFlowPreferred());
        assertTrue(gitlab.getClientSecret().isBlank());
        assertEquals("https://gitlab.com/oauth/authorize",gitlab.getAuthorizationUrl());
        assertEquals("https://gitlab.com/oauth/authorize_device",gitlab.getDeviceAuthorizationUrl());
    }

    @Test
    void authorizationStartLanguageIsBoundedToSupportedUiLanguages() {
        assertEquals("es",new RemoteAuthStartRequest("github","es-ES").language());
        assertEquals("en",new RemoteAuthStartRequest("github","fr-FR").language());
        assertEquals("en",new RemoteAuthStartRequest("github",null).language());
    }

    @Test
    void microsoftOneDrivePrefersInteractiveAuthorizationCodePkceForDesktopSignIn() {
        RemoteAuthProperties properties=new RemoteAuthProperties();
        assertFalse(properties.provider(RemoteStorageProviderType.ONEDRIVE).isDeviceFlowPreferred());
        assertTrue(properties.provider(RemoteStorageProviderType.ONEDRIVE).getClientSecret().isBlank());
        assertEquals("https://login.microsoftonline.com/{tenant}/oauth2/v2.0/devicecode",properties.provider(RemoteStorageProviderType.ONEDRIVE).getDeviceAuthorizationUrl());
    }

    @Test
    void pkceUsesHighEntropyVerifierAndS256Challenge() {
        String verifier=RemotePkce.verifier();String challenge=RemotePkce.challenge(verifier);
        assertTrue(verifier.length()>=43);assertTrue(challenge.length()>=43);assertFalse(verifier.equals(challenge));
        assertArrayEquals(RemotePkce.challenge(verifier).getBytes(),challenge.getBytes());
    }

    private PendingRemoteAuthFlow flow(String state,Instant expires){return PendingRemoteAuthFlow.authorizationCode(state,"github","github-oauth",RemoteStorageProviderType.GITHUB,"verifier","http://127.0.0.1:8081/api/remote-auth/callback",List.of("repo"),expires,"en");}
}
