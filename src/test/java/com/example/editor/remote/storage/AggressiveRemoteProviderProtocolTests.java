package com.example.editor.remote.storage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.net.http.HttpHeaders;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.example.editor.remote.storage.provider.*;
import tools.jackson.databind.json.JsonMapper;

/** Provider-specific protocol assertions. These are different from facade tests: they verify the real adapter endpoints and auth envelopes. */
class AggressiveRemoteProviderProtocolTests {
    @Test void everyCloudAdapterUsesItsProviderSpecificConnectionEndpointWithoutWriting(){
        var mapper=JsonMapper.builder().build();
        for(RemoteStorageProviderType type:List.of(RemoteStorageProviderType.GITHUB,RemoteStorageProviderType.GITLAB,RemoteStorageProviderType.ONEDRIVE,RemoteStorageProviderType.GOOGLE_DRIVE,RemoteStorageProviderType.DROPBOX)){
            RemoteStorageHttpClient http=mock(RemoteStorageHttpClient.class); when(http.sendSmall(anyString(),any(),anyMap(),nullable(byte[].class))).thenReturn(ok("{}"));
            RemoteArtifactStorageProvider provider=switch(type){case GITHUB->new GitHubRemoteStorageProvider(http,mapper);case GITLAB->new GitLabRemoteStorageProvider(http,mapper);case ONEDRIVE->new OneDriveRemoteStorageProvider(http,mapper);case GOOGLE_DRIVE->new GoogleDriveRemoteStorageProvider(http,mapper);case DROPBOX->new DropboxRemoteStorageProvider(http,mapper);default->throw new IllegalArgumentException();};
            RemoteStorageProperties.Profile p=profile(type); provider.testConnection("p",p,new RemoteStorageCredentials("secret-token"));
            var uri=org.mockito.ArgumentCaptor.forClass(java.net.URI.class);
            verify(http,times(1)).sendSmall(
                eq(type==RemoteStorageProviderType.DROPBOX?"POST":"GET"),
                uri.capture(),
                argThat(headers -> "Bearer secret-token".equals(headers.get("Authorization"))),
                nullable(byte[].class));
            assertFalse(uri.getValue().toString().contains("secret-token"));
            String url=uri.getValue().toString(); switch(type){case GITHUB->assertTrue(url.contains("/repos/owner/repo/branches/main"));case GITLAB->assertTrue(url.contains("/projects/group%2Fproject/repository/branches/main"));case ONEDRIVE->assertTrue(url.contains("/me/drive/root"));case GOOGLE_DRIVE->assertTrue(url.contains("/files/root"));case DROPBOX->assertTrue(url.endsWith("/users/get_current_account"));default->{} }
            verifyNoMoreInteractions(http);
        }
    }
    private static RemoteStorageProperties.Profile profile(RemoteStorageProviderType type){RemoteStorageProperties.Profile p=new RemoteStorageProperties.Profile();p.setProvider(type);p.setBaseUrl("https://example.test/api");p.setOwner("owner");p.setRepository("repo");p.setProjectId("group/project");p.setBranch("main");return p;}
    private static RemoteStorageHttpClient.BoundedResponse ok(String text){return new RemoteStorageHttpClient.BoundedResponse(200,HttpHeaders.of(Map.of("content-type",List.of("application/json")),(a,b)->true),text.getBytes(StandardCharsets.UTF_8));}
}
