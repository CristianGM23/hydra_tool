package com.example.editor.remote.storage;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import com.example.editor.remote.auth.RemoteAuthProperties;
import com.example.editor.remote.storage.provider.*;
import tools.jackson.databind.json.JsonMapper;

/**
 * Opt-in live smoke tests. They never contain credentials in source control and only run when
 * HYDRA_LIVE_REMOTE_TESTS=true. Each test writes a uniquely named small artifact and reads it back.
 */
@EnabledIfEnvironmentVariable(named="HYDRA_LIVE_REMOTE_TESTS", matches="(?i:true|1|yes)")
class LiveRemoteRepositoryRoundTripTests {
    @Test void githubLiveConnectionAndFileRoundTrip(){run(RemoteStorageProviderType.GITHUB);}
    @Test void gitlabLiveConnectionAndFileRoundTrip(){run(RemoteStorageProviderType.GITLAB);}
    @Test void oneDriveLiveConnectionAndFileRoundTrip(){run(RemoteStorageProviderType.ONEDRIVE);}
    @Test void googleDriveLiveConnectionAndFileRoundTrip(){run(RemoteStorageProviderType.GOOGLE_DRIVE);}
    @Test void dropboxLiveConnectionAndFileRoundTrip(){run(RemoteStorageProviderType.DROPBOX);}

    private void run(RemoteStorageProviderType type){
        String prefix="HYDRA_TEST_"+type.name()+"_"; String token=env(prefix+"TOKEN"); assumeTrue(!token.isBlank(),prefix+"TOKEN is required");
        RemoteStorageProperties properties=new RemoteStorageProperties(); properties.setConnectTimeout(Duration.ofSeconds(15)); properties.setRequestTimeout(Duration.ofSeconds(45)); properties.setMaximumArtifactBytes(2_000_000);
        RemoteStorageProperties.Profile profile=new RemoteStorageProperties.Profile(); profile.setProvider(type); profile.setToken(token); profile.setRootPath(env(prefix+"ROOT_PATH")); profile.setBaseUrl(env(prefix+"BASE_URL")); profile.setBranch(or(env(prefix+"BRANCH"),"main"));
        profile.setOwner(env(prefix+"OWNER")); profile.setRepository(env(prefix+"REPOSITORY")); profile.setProjectId(env(prefix+"PROJECT_ID")); profile.setDriveId(env(prefix+"DRIVE_ID")); profile.setRootItemId(env(prefix+"ROOT_ITEM_ID")); profile.setRepositoryUrl(env(prefix+"REPOSITORY_URL"));
        validateCoordinates(type,profile,prefix); String id=type.name().toLowerCase(Locale.ROOT); properties.setProfiles(Map.of(id,profile));
        RemoteAuthProperties auth=new RemoteAuthProperties(); var mapper=JsonMapper.builder().build(); var http=new RemoteStorageHttpClient(properties,auth);
        RemoteStorageProviderRegistry registry=new RemoteStorageProviderRegistry(List.of(new GitHubRemoteStorageProvider(http,mapper),new GitLabRemoteStorageProvider(http,mapper),new OneDriveRemoteStorageProvider(http,mapper),new GoogleDriveRemoteStorageProvider(http,mapper),new DropboxRemoteStorageProvider(http,mapper)));
        RemoteStorageService service=new RemoteStorageService(properties,registry,new ConfiguredRemoteStorageCredentialResolver());
        RemoteStorageConnectionTestResult connection=service.testConnection(id); assertTrue(connection.connected(),connection.message());
        String path="hydra-live-tests/"+type.name().toLowerCase(Locale.ROOT)+"-"+UUID.randomUUID()+".json"; byte[] payload=("{\"hydra\":true,\"provider\":\""+type+"\"}").getBytes(StandardCharsets.UTF_8);
        RemoteArtifactDescriptor descriptor=service.write(id,path,RemoteArtifactScope.LAYER,payload,"application/json",RemoteWriteMode.CREATE_ONLY,"","HYDRA live round-trip verification"); assertNotNull(descriptor.version());
        RemoteArtifact restored=service.read(id,path,RemoteArtifactScope.LAYER); assertArrayEquals(payload,restored.content()); assertEquals(path,restored.descriptor().path());
    }
    private static void validateCoordinates(RemoteStorageProviderType type,RemoteStorageProperties.Profile p,String prefix){switch(type){case GITHUB->{assumeTrue(!p.getOwner().isBlank(),prefix+"OWNER is required");assumeTrue(!p.getRepository().isBlank(),prefix+"REPOSITORY is required");}case GITLAB->assumeTrue(!p.getProjectId().isBlank(),prefix+"PROJECT_ID is required");default->{}}}
    private static String env(String key){return Optional.ofNullable(System.getenv(key)).orElse("").trim();} private static String or(String value,String fallback){return value.isBlank()?fallback:value;}
}
