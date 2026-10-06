package com.example.editor.remote.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

class RemoteStorageArchitectureTests {
    @Test
    void remotePathsAreNormalizedBelowTheConfiguredRootAndRejectTraversal() {
        assertEquals("HYDRA/layers/people.evl",RemotePathPolicy.normalize("/HYDRA/","layers\\people.evl"));
        assertThrows(RemoteStorageException.class,()->RemotePathPolicy.normalize("HYDRA","../secret.txt"));
        assertThrows(RemoteStorageException.class,()->RemotePathPolicy.normalize("HYDRA","layers/./secret.txt"));
    }

    @Test
    void publicProfileDescriptorsCannotExposeTokensOrCredentialObjects() {
        List<String> names=Arrays.stream(RemoteStorageProfileDescriptor.class.getRecordComponents()).map(java.lang.reflect.RecordComponent::getName).toList();
        assertFalse(names.contains("token"));
        assertFalse(names.contains("credentials"));
        assertFalse(names.contains("authorization"));
    }

    @Test
    void storageFacadeResolvesCredentialsThroughAnSpiAndEnforcesTheArtifactLimit() {
        RemoteStorageProperties properties=new RemoteStorageProperties();
        properties.setMaximumArtifactBytes(4);
        RemoteStorageProperties.Profile profile=new RemoteStorageProperties.Profile();
        profile.setProvider(RemoteStorageProviderType.GITHUB);
        profile.setToken("server-secret");
        profile.setRootPath("HYDRA");
        properties.setProfiles(Map.of("github",profile));
        AtomicReference<RemoteStorageCredentials> seen=new AtomicReference<>();
        AtomicReference<String> path=new AtomicReference<>();
        RemoteArtifactStorageProvider provider=new RemoteArtifactStorageProvider() {
            @Override public RemoteStorageProviderType type(){return RemoteStorageProviderType.GITHUB;}
            @Override public RemoteArtifact read(String profileId,RemoteStorageProperties.Profile configured,RemoteStorageCredentials credentials,String normalizedPath,RemoteArtifactScope scope,long maximumBytes){throw new UnsupportedOperationException();}
            @Override public RemoteArtifactDescriptor write(String profileId,RemoteStorageProperties.Profile configured,RemoteStorageCredentials credentials,RemoteArtifactScope scope,RemoteArtifactWriteCommand command){seen.set(credentials);path.set(command.path());return new RemoteArtifactDescriptor(profileId,type(),scope,command.path(),"v1",command.content().length,command.contentType(),"",true);}
        };
        RemoteStorageService service=new RemoteStorageService(properties,new RemoteStorageProviderRegistry(List.of(provider)),new ConfiguredRemoteStorageCredentialResolver());
        service.write("github","layers/test.json",RemoteArtifactScope.LAYER,new byte[]{1,2,3,4},"application/json",RemoteWriteMode.UPSERT,"","test");
        assertEquals("server-secret",seen.get().token());
        assertEquals("HYDRA/layers/test.json",path.get());
        assertThrows(RemoteStorageException.class,()->service.write("github","too-large.bin",RemoteArtifactScope.LAYER,new byte[]{1,2,3,4,5},"application/octet-stream",RemoteWriteMode.UPSERT,"","test"));
    }

    @Test
    void safeProfileListingReportsConfigurationWithoutReturningTheSecret() {
        RemoteStorageProperties properties=new RemoteStorageProperties();
        RemoteStorageProperties.Profile profile=new RemoteStorageProperties.Profile();
        profile.setProvider(RemoteStorageProviderType.GITLAB);
        profile.setToken("top-secret");
        profile.setProjectId("group/project");
        properties.setProfiles(Map.of("gitlab",profile));
        RemoteStorageService service=new RemoteStorageService(properties,new RemoteStorageProviderRegistry(List.of()),new ConfiguredRemoteStorageCredentialResolver());
        RemoteStorageProfileDescriptor descriptor=service.profiles().get(0);
        assertTrue(descriptor.configured());
        assertEquals("group/project",descriptor.repository());
        assertFalse(descriptor.toString().contains("top-secret"));
    }
    @Test
    void repositoryConnectionTestUsesProviderWithoutMutatingRemoteContent() {
        RemoteStorageProperties properties=new RemoteStorageProperties();
        RemoteStorageProperties.Profile profile=new RemoteStorageProperties.Profile();
        profile.setProvider(RemoteStorageProviderType.GITHUB);
        profile.setToken("server-secret");
        profile.setOwner("owner");
        profile.setRepository("repository");
        properties.setProfiles(Map.of("github",profile));
        java.util.concurrent.atomic.AtomicBoolean tested=new java.util.concurrent.atomic.AtomicBoolean();
        RemoteArtifactStorageProvider provider=new RemoteArtifactStorageProvider() {
            @Override public RemoteStorageProviderType type(){return RemoteStorageProviderType.GITHUB;}
            @Override public void testConnection(String profileId,RemoteStorageProperties.Profile configured,RemoteStorageCredentials credentials){
                tested.set(true);
                assertEquals("server-secret",credentials.token());
            }
            @Override public RemoteArtifact read(String profileId,RemoteStorageProperties.Profile configured,RemoteStorageCredentials credentials,String normalizedPath,RemoteArtifactScope scope,long maximumBytes){throw new AssertionError("Connection test must not read an application artifact");}
            @Override public RemoteArtifactDescriptor write(String profileId,RemoteStorageProperties.Profile configured,RemoteStorageCredentials credentials,RemoteArtifactScope scope,RemoteArtifactWriteCommand command){throw new AssertionError("Connection test must not write remote content");}
        };
        RemoteStorageService service=new RemoteStorageService(properties,new RemoteStorageProviderRegistry(List.of(provider)),new ConfiguredRemoteStorageCredentialResolver());
        RemoteStorageConnectionTestResult result=service.testConnection("github");
        assertTrue(tested.get());
        assertTrue(result.connected());
        assertTrue(result.credentialsConfigured());
        assertEquals(RemoteStorageProviderType.GITHUB,result.provider());
    }

    @Test
    void repositoryConnectionFailureIsReturnedAsSafeStatus() {
        RemoteStorageProperties properties=new RemoteStorageProperties();
        RemoteStorageProperties.Profile profile=new RemoteStorageProperties.Profile();
        profile.setProvider(RemoteStorageProviderType.GITLAB);
        profile.setToken("server-secret");
        profile.setProjectId("group/project");
        properties.setProfiles(Map.of("gitlab",profile));
        RemoteArtifactStorageProvider provider=new RemoteArtifactStorageProvider() {
            @Override public RemoteStorageProviderType type(){return RemoteStorageProviderType.GITLAB;}
            @Override public void testConnection(String profileId,RemoteStorageProperties.Profile configured,RemoteStorageCredentials credentials){throw new RemoteStorageException(RemoteStorageException.Reason.REMOTE_ERROR,"Remote endpoint rejected the connection");}
            @Override public RemoteArtifact read(String profileId,RemoteStorageProperties.Profile configured,RemoteStorageCredentials credentials,String normalizedPath,RemoteArtifactScope scope,long maximumBytes){throw new UnsupportedOperationException();}
            @Override public RemoteArtifactDescriptor write(String profileId,RemoteStorageProperties.Profile configured,RemoteStorageCredentials credentials,RemoteArtifactScope scope,RemoteArtifactWriteCommand command){throw new UnsupportedOperationException();}
        };
        RemoteStorageService service=new RemoteStorageService(properties,new RemoteStorageProviderRegistry(List.of(provider)),new ConfiguredRemoteStorageCredentialResolver());
        RemoteStorageConnectionTestResult result=service.testConnection("gitlab");
        assertFalse(result.connected());
        assertTrue(result.credentialsConfigured());
        assertEquals("Remote endpoint rejected the connection",result.message());
        assertFalse(result.toString().contains("server-secret"));
    }

}
