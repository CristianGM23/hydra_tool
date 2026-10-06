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

class MicrosoftOneDriveOAuthProviderTests {
    @Test
    void resolvesOrganizationPlaceholderInConfiguredMicrosoftEndpoint() {
        RemoteAuthProperties properties=new RemoteAuthProperties();
        CapturingHttpClient http=new CapturingHttpClient(properties,success());
        MicrosoftOneDriveOAuthProvider provider=new MicrosoftOneDriveOAuthProvider(http,JsonMapper.builder().findAndAddModules().build());
        RemoteAuthProperties.Provider auth=properties.provider(RemoteStorageProviderType.ONEDRIVE);
        auth.setClientId("11111111-2222-3333-4444-555555555555");
        auth.setTenant("organizations");
        auth.setDeviceAuthorizationUrl("https://login.microsoftonline.com/{tenant}/oauth2/v2.0/devicecode");
        RemoteDeviceAuthorization device=provider.beginDeviceAuthorization(storage(),auth,List.of("offline_access","Files.ReadWrite"));
        assertEquals("https://login.microsoftonline.com/organizations/oauth2/v2.0/devicecode",http.lastUri.toString());
        assertEquals("ABCD-EFGH",device.userCode());
    }

    @Test
    void convertsMissingOrganizationResponseIntoActionableInstallationError() {
        RemoteAuthProperties properties=new RemoteAuthProperties();
        RemoteOAuthHttpClient.Response response=response(400,"{\"error\":\"invalid_request\",\"error_description\":\"AADSTS50059: No tenant-identifying information found\",\"error_codes\":[50059]}");
        CapturingHttpClient http=new CapturingHttpClient(properties,response);
        MicrosoftOneDriveOAuthProvider provider=new MicrosoftOneDriveOAuthProvider(http,JsonMapper.builder().findAndAddModules().build());
        RemoteAuthProperties.Provider auth=properties.provider(RemoteStorageProviderType.ONEDRIVE);
        auth.setClientId("11111111-2222-3333-4444-555555555555");
        RemoteAuthException error=assertThrows(RemoteAuthException.class,()->provider.beginDeviceAuthorization(storage(),auth,List.of("offline_access","Files.ReadWrite")));
        assertEquals(RemoteAuthException.Reason.PROVIDER_NOT_CONFIGURED,error.getReason());
        assertTrue(error.getMessage().contains("organization directory ID"));
        assertTrue(!error.getMessage().toLowerCase(java.util.Locale.ROOT).contains("tenant"));
    }

    private static RemoteStorageProperties.Profile storage(){RemoteStorageProperties.Profile profile=new RemoteStorageProperties.Profile();profile.setProvider(RemoteStorageProviderType.ONEDRIVE);return profile;}
    private static RemoteOAuthHttpClient.Response success(){return response(200,"{\"device_code\":\"device-secret\",\"user_code\":\"ABCD-EFGH\",\"verification_uri\":\"https://microsoft.com/devicelogin\",\"expires_in\":900,\"interval\":5}");}
    private static RemoteOAuthHttpClient.Response response(int status,String body){return new RemoteOAuthHttpClient.Response(status,"application/json",body.getBytes(StandardCharsets.UTF_8));}

    private static final class CapturingHttpClient extends RemoteOAuthHttpClient {
        private URI lastUri;
        private final Response response;
        private CapturingHttpClient(RemoteAuthProperties properties,Response response){super(properties);this.response=response;}
        @Override public Response postForm(URI uri,Map<String,String> values,Map<String,String> headers){lastUri=uri;return response;}
    }
}
