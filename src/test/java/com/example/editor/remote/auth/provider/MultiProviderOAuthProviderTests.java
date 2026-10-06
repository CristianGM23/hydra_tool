package com.example.editor.remote.auth.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.example.editor.remote.auth.RemoteAuthException;
import com.example.editor.remote.auth.RemoteAuthProperties;
import com.example.editor.remote.auth.RemoteOAuthHttpClient;
import com.example.editor.remote.storage.RemoteStorageProperties;
import com.example.editor.remote.storage.RemoteStorageProviderType;
import tools.jackson.databind.json.JsonMapper;

class MultiProviderOAuthProviderTests {
    @Test
    void githubExplainsWhenDeviceFlowIsDisabled() {
        RemoteAuthProperties properties=new RemoteAuthProperties();
        QueueHttpClient http=new QueueHttpClient(properties,response(200,"{\"error\":\"device_flow_disabled\",\"error_description\":\"Device Flow is not enabled\"}"));
        GitHubOAuthProvider provider=new GitHubOAuthProvider(http,JsonMapper.builder().findAndAddModules().build());
        RemoteAuthProperties.Provider auth=properties.provider(RemoteStorageProviderType.GITHUB);auth.setClientId("Iv1.0123456789abcdef");
        RemoteAuthException error=assertThrows(RemoteAuthException.class,()->provider.beginDeviceAuthorization(storage(RemoteStorageProviderType.GITHUB),auth,List.of("repo")));
        assertEquals(RemoteAuthException.Reason.PROVIDER_NOT_CONFIGURED,error.getReason());
        assertTrue(error.getMessage().contains("enable Device Flow"));
    }

    @Test
    void gitlabExplainsWhenSelfManagedServerDoesNotExposeDeviceGrant() {
        RemoteAuthProperties properties=new RemoteAuthProperties();
        QueueHttpClient http=new QueueHttpClient(properties,response(404,"{\"error\":\"not_found\"}"));
        GitLabOAuthProvider provider=new GitLabOAuthProvider(http,JsonMapper.builder().findAndAddModules().build());
        RemoteAuthProperties.Provider auth=properties.provider(RemoteStorageProviderType.GITLAB);auth.setClientId("public-gitlab-application-id");
        RemoteStorageProperties.Profile storage=storage(RemoteStorageProviderType.GITLAB);storage.setBaseUrl("https://gitlab.example.com/api/v4");
        RemoteAuthException error=assertThrows(RemoteAuthException.class,()->provider.beginDeviceAuthorization(storage,auth,List.of("api")));
        assertEquals(RemoteAuthException.Reason.PROVIDER_NOT_CONFIGURED,error.getReason());
        assertTrue(error.getMessage().contains("browser authorization with PKCE"));
        assertEquals("https://gitlab.example.com/oauth/authorize_device",http.lastUri.toString());
    }

    @Test
    void googleMapsInvalidClientBeforeAnyCredentialCanBeStored() {
        RemoteAuthProperties properties=new RemoteAuthProperties();
        QueueHttpClient http=new QueueHttpClient(properties,response(401,"{\"error\":\"invalid_client\",\"error_description\":\"The OAuth client was not found\"}"));
        GoogleDriveOAuthProvider provider=new GoogleDriveOAuthProvider(http,JsonMapper.builder().findAndAddModules().build());
        RemoteAuthProperties.Provider auth=properties.provider(RemoteStorageProviderType.GOOGLE_DRIVE);auth.setClientId("123456789012-abc123.apps.googleusercontent.com");
        RemoteAuthException error=assertThrows(RemoteAuthException.class,()->provider.exchange(storage(RemoteStorageProviderType.GOOGLE_DRIVE),auth,"http://127.0.0.1:8081/api/remote-auth/callback","code","verifier",List.of("openid")));
        assertEquals(RemoteAuthException.Reason.PROVIDER_NOT_CONFIGURED,error.getReason());
        assertTrue(error.getMessage().contains("Google rejected the OAuth client"));
    }

    @Test
    void dropboxMapsRedirectMismatchIntoExactSetupInstruction() {
        RemoteAuthProperties properties=new RemoteAuthProperties();
        QueueHttpClient http=new QueueHttpClient(properties,response(400,"{\"error\":\"redirect_uri_mismatch\",\"error_description\":\"redirect_uri mismatch\"}"));
        DropboxOAuthProvider provider=new DropboxOAuthProvider(http,JsonMapper.builder().findAndAddModules().build());
        RemoteAuthProperties.Provider auth=properties.provider(RemoteStorageProviderType.DROPBOX);auth.setClientId("abc123def456ghi");
        RemoteAuthException error=assertThrows(RemoteAuthException.class,()->provider.exchange(storage(RemoteStorageProviderType.DROPBOX),auth,"http://127.0.0.1:8081/api/remote-auth/callback","code","verifier",List.of("files.content.read")));
        assertEquals(RemoteAuthException.Reason.PROVIDER_NOT_CONFIGURED,error.getReason());
        assertTrue(error.getMessage().contains("exact HYDRA callback URL"));
    }

    @Test
    void browserAuthorizationUrisKeepStateAndPkceForAllSupportedBrowserProviders() {
        RemoteAuthProperties properties=new RemoteAuthProperties();
        QueueHttpClient http=new QueueHttpClient(properties,response(200,"{}"));
        String redirect="http://127.0.0.1:8081/api/remote-auth/callback";
        RemoteAuthProperties.Provider gitlab=properties.provider(RemoteStorageProviderType.GITLAB);gitlab.setClientId("public-gitlab-application-id");
        URI gitlabUri=new GitLabOAuthProvider(http,JsonMapper.builder().findAndAddModules().build()).authorizationUri(storage(RemoteStorageProviderType.GITLAB),gitlab,redirect,"state-123","challenge-123",List.of("api"));
        assertTrue(gitlabUri.toString().contains("state=state-123"));assertTrue(gitlabUri.toString().contains("code_challenge=challenge-123"));assertTrue(gitlabUri.toString().contains("code_challenge_method=S256"));

        RemoteAuthProperties.Provider google=properties.provider(RemoteStorageProviderType.GOOGLE_DRIVE);google.setClientId("123456789012-abc123.apps.googleusercontent.com");
        URI googleUri=new GoogleDriveOAuthProvider(http,JsonMapper.builder().findAndAddModules().build()).authorizationUri(storage(RemoteStorageProviderType.GOOGLE_DRIVE),google,redirect,"state-123","challenge-123",List.of("openid"));
        assertTrue(googleUri.toString().contains("access_type=offline"));assertTrue(googleUri.toString().contains("code_challenge_method=S256"));

        RemoteAuthProperties.Provider dropbox=properties.provider(RemoteStorageProviderType.DROPBOX);dropbox.setClientId("abc123def456ghi");
        URI dropboxUri=new DropboxOAuthProvider(http,JsonMapper.builder().findAndAddModules().build()).authorizationUri(storage(RemoteStorageProviderType.DROPBOX),dropbox,redirect,"state-123","challenge-123",List.of("files.content.read"));
        assertTrue(dropboxUri.toString().contains("token_access_type=offline"));assertTrue(dropboxUri.toString().contains("code_challenge_method=S256"));
    }

    private static RemoteStorageProperties.Profile storage(RemoteStorageProviderType type){RemoteStorageProperties.Profile profile=new RemoteStorageProperties.Profile();profile.setProvider(type);return profile;}
    private static RemoteOAuthHttpClient.Response response(int status,String body){return new RemoteOAuthHttpClient.Response(status,"application/json",body.getBytes(StandardCharsets.UTF_8));}

    private static final class QueueHttpClient extends RemoteOAuthHttpClient {
        private final Response response;private URI lastUri;
        private QueueHttpClient(RemoteAuthProperties properties,Response response){super(properties);this.response=response;}
        @Override public Response postForm(URI uri,Map<String,String> values,Map<String,String> headers){this.lastUri=uri;return response;}
    }
}
