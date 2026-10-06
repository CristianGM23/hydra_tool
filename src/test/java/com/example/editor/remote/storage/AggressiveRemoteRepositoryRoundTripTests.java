package com.example.editor.remote.storage;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Metamorphic/adversarial remote-repository tests: connection + write/read/update/conflict across every provider contract. */
class AggressiveRemoteRepositoryRoundTripTests {
    @Test void everyRemoteProviderSurvivesConnectionExportImportUpdateAndConflictRoundTrip() throws Exception {
        for (RemoteStorageProviderType type : RemoteStorageProviderType.values()) {
            StatefulProvider provider = new StatefulProvider(type);
            RemoteStorageProperties properties = properties(type);
            RemoteStorageService service = new RemoteStorageService(properties, new RemoteStorageProviderRegistry(List.of(provider)), new ConfiguredRemoteStorageCredentialResolver());
            String profile = type.name().toLowerCase(Locale.ROOT);
            assertTrue(service.testConnection(profile).connected(), type + " connectivity");
            byte[] first = ("{\"provider\":\""+type+"\",\"revision\":1}").getBytes(StandardCharsets.UTF_8);
            RemoteArtifactDescriptor created = service.write(profile,"layers/model folder/a#b?.json",RemoteArtifactScope.LAYER,first,"application/json",RemoteWriteMode.CREATE_ONLY,"","initial export");
            assertTrue(created.created());
            RemoteArtifact downloaded = service.read(profile,"layers/model folder/a#b?.json",RemoteArtifactScope.LAYER);
            assertArrayEquals(first,downloaded.content(),type+" exported bytes must import unchanged");
            assertEquals(sha(first),sha(downloaded.content()));
            byte[] second=("{\"provider\":\""+type+"\",\"revision\":2}").getBytes(StandardCharsets.UTF_8);
            RemoteArtifactDescriptor updated=service.write(profile,"layers/model folder/a#b?.json",RemoteArtifactScope.LAYER,second,"application/json",RemoteWriteMode.UPDATE_ONLY,created.version(),"update");
            assertFalse(updated.created());
            assertArrayEquals(second,service.read(profile,"layers/model folder/a#b?.json",RemoteArtifactScope.LAYER).content());
            RemoteStorageException stale=assertThrows(RemoteStorageException.class,()->service.write(profile,"layers/model folder/a#b?.json",RemoteArtifactScope.LAYER,first,"application/json",RemoteWriteMode.UPDATE_ONLY,created.version(),"stale"));
            assertEquals(RemoteStorageException.Reason.PRECONDITION_FAILED,stale.getReason());
            assertThrows(RemoteStorageException.class,()->service.write(profile,"../escape.txt",RemoteArtifactScope.LAYER,first,"text/plain",RemoteWriteMode.UPSERT,"","escape"));
            assertEquals(1,provider.connectionTests.get(),type+" testConnection must be non-mutating");
        }
    }

    @Test void remoteRoundTripKeepsProfilesIsolatedUnderConcurrentWrites() throws Exception {
        RemoteStorageProperties properties=new RemoteStorageProperties(); properties.setMaximumConcurrentTransfers(8); properties.setMaximumArtifactBytes(1_000_000);
        List<RemoteArtifactStorageProvider> providers=new ArrayList<>(); Map<String,RemoteStorageProperties.Profile> profiles=new LinkedHashMap<>();
        for(RemoteStorageProviderType type:RemoteStorageProviderType.values()){providers.add(new StatefulProvider(type)); profiles.put(type.name().toLowerCase(Locale.ROOT),profile(type));}
        properties.setProfiles(profiles); RemoteStorageService service=new RemoteStorageService(properties,new RemoteStorageProviderRegistry(providers),new ConfiguredRemoteStorageCredentialResolver());
        var tasks=new ArrayList<Thread>(); var failures=Collections.synchronizedList(new ArrayList<Throwable>());
        for(RemoteStorageProviderType type:RemoteStorageProviderType.values()) for(int i=0;i<20;i++){int n=i; Thread t=new Thread(()->{try{String id=type.name().toLowerCase(Locale.ROOT); byte[] body=(type+":"+n).getBytes(StandardCharsets.UTF_8); service.write(id,"stress/f"+n+".bin",RemoteArtifactScope.LAYER,body,"application/octet-stream",RemoteWriteMode.UPSERT,"","stress"); assertArrayEquals(body,service.read(id,"stress/f"+n+".bin",RemoteArtifactScope.LAYER).content());}catch(Throwable e){failures.add(e);}}); tasks.add(t);t.start();}
        for(Thread t:tasks)t.join(); assertTrue(failures.isEmpty(),failures.toString());
    }

    private static RemoteStorageProperties properties(RemoteStorageProviderType type){RemoteStorageProperties p=new RemoteStorageProperties();p.setMaximumArtifactBytes(1_000_000);p.setProfiles(Map.of(type.name().toLowerCase(Locale.ROOT),profile(type)));return p;}
    private static RemoteStorageProperties.Profile profile(RemoteStorageProviderType type){RemoteStorageProperties.Profile p=new RemoteStorageProperties.Profile();p.setProvider(type);p.setToken("test-token");p.setRootPath("HYDRA");p.setOwner("owner");p.setRepository("repo");p.setProjectId("group/project");p.setRepositoryUrl("https://example.invalid/repo.git");return p;}
    private static String sha(byte[] bytes)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}

    private static final class StatefulProvider implements RemoteArtifactStorageProvider {
        private final RemoteStorageProviderType type; private final Map<String,Versioned> data=new ConcurrentHashMap<>(); private final AtomicInteger versions=new AtomicInteger(); private final AtomicInteger connectionTests=new AtomicInteger();
        private StatefulProvider(RemoteStorageProviderType type){this.type=type;} @Override public RemoteStorageProviderType type(){return type;}
        @Override public void testConnection(String profileId,RemoteStorageProperties.Profile profile,RemoteStorageCredentials credentials){assertTrue(credentials.configured());connectionTests.incrementAndGet();}
        @Override public RemoteArtifact read(String profileId,RemoteStorageProperties.Profile profile,RemoteStorageCredentials credentials,String path,RemoteArtifactScope scope,long maximumBytes){Versioned v=data.get(profileId+"|"+path);if(v==null)throw new RemoteStorageException(RemoteStorageException.Reason.NOT_FOUND,"missing");return new RemoteArtifact(new RemoteArtifactDescriptor(profileId,type,scope,path,v.version,v.content.length,"application/octet-stream","",false),v.content.clone());}
        @Override public RemoteArtifactDescriptor write(String profileId,RemoteStorageProperties.Profile profile,RemoteStorageCredentials credentials,RemoteArtifactScope scope,RemoteArtifactWriteCommand command){String key=profileId+"|"+command.path();Versioned old=data.get(key);if(command.mode()==RemoteWriteMode.CREATE_ONLY&&old!=null)throw new RemoteStorageException(RemoteStorageException.Reason.CONFLICT,"exists");if(command.mode()==RemoteWriteMode.UPDATE_ONLY&&old==null)throw new RemoteStorageException(RemoteStorageException.Reason.NOT_FOUND,"missing");if(!command.expectedVersion().isBlank()&&(old==null||!command.expectedVersion().equals(old.version)))throw new RemoteStorageException(RemoteStorageException.Reason.PRECONDITION_FAILED,"stale");String version="v"+versions.incrementAndGet();data.put(key,new Versioned(version,command.content().clone()));return new RemoteArtifactDescriptor(profileId,type,scope,command.path(),version,command.contentLength(),command.contentType(),"",old==null);}
        private record Versioned(String version,byte[] content){}
    }
}
