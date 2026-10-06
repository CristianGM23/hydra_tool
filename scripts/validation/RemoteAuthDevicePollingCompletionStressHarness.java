package com.example.editor.remote.auth;

import com.example.editor.remote.auth.provider.*;
import com.example.editor.remote.storage.RemoteStorageProperties;
import com.example.editor.remote.storage.RemoteStorageProviderType;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Stress de finalización, serialización de device polling y refresh OAuth single-flight. */
public final class RemoteAuthDevicePollingCompletionStressHarness {
    private static final AtomicInteger checks=new AtomicInteger();
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);checks.incrementAndGet();}

    public static void main(String[] args) throws Exception {
        concurrentAuthorizationIsSingleFlightAndReplayable();
        pendingResponseAppliesBackoffWithoutDuplicatePolls();
        terminalFailuresAreReplayable();
        vaultFailureIsTerminalAndReplayable();
        callerInterruptionDoesNotDuplicateProviderPoll();
        authorizationAfterLocalExpiryIsTerminalAndReplayable();
        refreshIsSingleFlightAndWaitersRemainBounded();
        expiredRefreshWaiterFailsFast();
        interruptedRefreshWaiterPreservesCancellation();
        boundedStoresRemainBounded();
        System.out.println("REMOTE_AUTH_COMPLETION_STRESS="+checks.get()+"/"+checks.get()+" PASS");
    }

    private static Fixture fixture(FakeProvider provider,MemoryVault vault){
        RemoteAuthProperties auth=new RemoteAuthProperties();
        auth.setMaximumPendingFlows(128);auth.setDevicePollLockStripes(256);auth.setDevicePollLockWait(Duration.ofMillis(25));auth.setRefreshLockStripes(256);auth.setRefreshLockWait(Duration.ofMillis(50));
        RemoteStorageProperties storage=new RemoteStorageProperties();RemoteStorageProperties.Profile profile=new RemoteStorageProperties.Profile();profile.setProvider(RemoteStorageProviderType.GITHUB);profile.setEnabled(true);profile.setConnectionId("conn");profile.setDisplayName("GitHub");storage.getProfiles().put("github",profile);
        RemoteAuthFlowStore flows=new RemoteAuthFlowStore(auth);RemoteAuthOutcomeStore outcomes=new RemoteAuthOutcomeStore(auth);
        RemoteOAuthProviderRegistry registry=new RemoteOAuthProviderRegistry(List.of(provider));
        return new Fixture(auth,profile,flows,outcomes,provider,vault,new RemoteAuthService(auth,storage,registry,vault,flows,outcomes));
    }

    private static PendingRemoteAuthFlow flow(String state){return flow(state,Instant.now().plusSeconds(60));}
    private static PendingRemoteAuthFlow flow(String state,Instant expiresAt){return new PendingRemoteAuthFlow(state,"github","conn",RemoteStorageProviderType.GITHUB,"","",List.of("repo"),true,"device-secret",1,Instant.EPOCH,expiresAt,"en");}
    private static RemoteCredentialMaterial credential(){return credential("access",Instant.now().plusSeconds(3600));}
    private static RemoteCredentialMaterial credential(String access,Instant expires){return new RemoteCredentialMaterial(RemoteStorageProviderType.GITHUB,access,"refresh","Bearer",expires,List.of("repo"),"42","tester",Instant.now());}

    private static void concurrentAuthorizationIsSingleFlightAndReplayable() throws Exception {
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);FakeProvider provider=new FakeProvider(Behavior.AUTHORIZED,entered,release);MemoryVault vault=new MemoryVault(false);Fixture f=fixture(provider,vault);f.flows.put(flow("ok"));
        ExecutorService pool=Executors.newFixedThreadPool(32);CountDownLatch go=new CountDownLatch(1);List<Future<RemoteAuthPollResponse>> futures=new ArrayList<>();
        for(int i=0;i<32;i++)futures.add(pool.submit(()->{go.await();return f.service.poll(new RemoteAuthPollRequest("ok"));}));
        go.countDown();check(entered.await(2,TimeUnit.SECONDS),"one provider poll must start");Thread.sleep(80);check(provider.polls.get()==1,"concurrent polls for one state must be single-flight");release.countDown();
        int complete=0,pending=0;for(Future<RemoteAuthPollResponse> future:futures){RemoteAuthPollResponse value=future.get(5,TimeUnit.SECONDS);if(value.completed())complete++;if(value.pending())pending++;}
        pool.shutdown();check(pool.awaitTermination(5,TimeUnit.SECONDS),"poll pool must terminate");check(complete>=1,"one poll must observe authorization");check(complete+pending==32,"all callers must receive a stable response");check(provider.polls.get()==1,"provider must be called exactly once");check(vault.stores.get()==1,"credential must be stored exactly once");check(f.flows.size()==0,"authorized flow must be consumed");check(f.outcomes.size()==1,"authorized outcome must be retained ephemerally");
        for(int i=0;i<200;i++){RemoteAuthPollResponse replay=f.service.poll(new RemoteAuthPollRequest("ok"));check(replay.completed()&&!replay.pending(),"terminal success must be replayable");}
        check(provider.polls.get()==1,"replayed success must never poll provider again");
    }

    private static void pendingResponseAppliesBackoffWithoutDuplicatePolls() throws Exception {
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);FakeProvider provider=new FakeProvider(Behavior.PENDING,entered,release);Fixture f=fixture(provider,new MemoryVault(false));f.flows.put(flow("pending"));
        ExecutorService pool=Executors.newFixedThreadPool(24);CountDownLatch go=new CountDownLatch(1);List<Future<RemoteAuthPollResponse>> futures=new ArrayList<>();for(int i=0;i<24;i++)futures.add(pool.submit(()->{go.await();return f.service.poll(new RemoteAuthPollRequest("pending"));}));go.countDown();check(entered.await(2,TimeUnit.SECONDS),"pending provider poll must start");Thread.sleep(60);check(provider.polls.get()==1,"pending concurrent polls must single-flight");release.countDown();for(Future<RemoteAuthPollResponse> future:futures)check(future.get(5,TimeUnit.SECONDS).pending(),"all pending callers remain pending");pool.shutdown();check(pool.awaitTermination(5,TimeUnit.SECONDS),"pending pool must terminate");
        for(int i=0;i<100;i++)check(f.service.poll(new RemoteAuthPollRequest("pending")).pending(),"backoff window must return pending locally");check(provider.polls.get()==1,"backoff must suppress provider calls");check(f.flows.size()==1,"pending flow remains bounded and active");
    }

    private static void terminalFailuresAreReplayable(){
        FakeProvider provider=new FakeProvider(Behavior.DENIED,null,null);Fixture f=fixture(provider,new MemoryVault(false));f.flows.put(flow("denied"));
        for(int i=0;i<50;i++){try{f.service.poll(new RemoteAuthPollRequest("denied"));throw new AssertionError("denied must fail");}catch(RemoteAuthException e){check(e.getReason()==RemoteAuthException.Reason.TOKEN_EXCHANGE_FAILED,"denied reason must remain stable");}}
        check(provider.polls.get()==1,"terminal denial must be polled once");check(f.flows.size()==0,"denied flow must be removed");check(f.outcomes.size()==1,"denial outcome must be retained");
    }

    private static void vaultFailureIsTerminalAndReplayable(){
        FakeProvider provider=new FakeProvider(Behavior.AUTHORIZED,null,null);MemoryVault vault=new MemoryVault(true);Fixture f=fixture(provider,vault);f.flows.put(flow("vault-fail"));
        for(int i=0;i<40;i++){try{f.service.poll(new RemoteAuthPollRequest("vault-fail"));throw new AssertionError("vault failure expected");}catch(RemoteAuthException e){check(e.getReason()==RemoteAuthException.Reason.VAULT_UNAVAILABLE,"vault failure reason must remain stable");}}
        check(provider.polls.get()==1,"vault failure must not repoll provider");check(vault.stores.get()==1,"vault write attempted once");check(f.flows.size()==0,"consumed flow must not revive after vault failure");check(f.outcomes.size()==1,"vault failure outcome must be retained");
    }

    private static void callerInterruptionDoesNotDuplicateProviderPoll() throws Exception {
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);FakeProvider provider=new FakeProvider(Behavior.AUTHORIZED,entered,release);Fixture f=fixture(provider,new MemoryVault(false));f.auth.setDevicePollLockWait(Duration.ofSeconds(3));f.flows.put(flow("interrupt"));
        AtomicReference<Throwable> firstError=new AtomicReference<>();Thread first=Thread.ofPlatform().start(()->{try{f.service.poll(new RemoteAuthPollRequest("interrupt"));}catch(Throwable t){firstError.set(t);}});check(entered.await(2,TimeUnit.SECONDS),"first poll must hold state lock");
        AtomicReference<RemoteAuthException> secondError=new AtomicReference<>();AtomicBoolean preserved=new AtomicBoolean();Thread second=Thread.ofPlatform().start(()->{try{f.service.poll(new RemoteAuthPollRequest("interrupt"));}catch(RemoteAuthException e){secondError.set(e);preserved.set(Thread.currentThread().isInterrupted());}});Thread.sleep(75);second.interrupt();second.join(2000);check(!second.isAlive(),"interrupted waiter must terminate promptly");check(secondError.get()!=null&&secondError.get().getReason()==RemoteAuthException.Reason.REMOTE_ERROR,"interrupted waiter must get cancellation-safe error");check(preserved.get(),"interrupted polling thread must preserve interrupt flag");check(provider.polls.get()==1,"interrupted waiter must not duplicate provider call");release.countDown();first.join(3000);check(!first.isAlive()&&firstError.get()==null,"owner poll must still finish successfully");check(provider.polls.get()==1,"provider remains single-flight after interruption");
    }

    private static void authorizationAfterLocalExpiryIsTerminalAndReplayable() throws Exception {
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);FakeProvider provider=new FakeProvider(Behavior.AUTHORIZED,entered,release);Fixture f=fixture(provider,new MemoryVault(false));f.flows.put(flow("expires-during-provider",Instant.now().plusMillis(120)));
        ExecutorService pool=Executors.newSingleThreadExecutor();Future<?> future=pool.submit(()->f.service.poll(new RemoteAuthPollRequest("expires-during-provider")));check(entered.await(2,TimeUnit.SECONDS),"provider must start before local flow expiry");Thread.sleep(180);release.countDown();
        try{future.get(3,TimeUnit.SECONDS);throw new AssertionError("local expiry during provider call must remain terminal");}catch(ExecutionException e){check(e.getCause() instanceof RemoteAuthException&&((RemoteAuthException)e.getCause()).getReason()==RemoteAuthException.Reason.FLOW_EXPIRED,"expiry during authorized provider call must surface FLOW_EXPIRED");}
        pool.shutdown();check(pool.awaitTermination(3,TimeUnit.SECONDS),"expiry worker must terminate");check(f.flows.size()==0,"expired device flow must be consumed/removed");check(f.outcomes.size()==1,"expiry during provider call must create terminal outcome");
        for(int i=0;i<25;i++){try{f.service.poll(new RemoteAuthPollRequest("expires-during-provider"));throw new AssertionError("expired outcome must replay");}catch(RemoteAuthException e){check(e.getReason()==RemoteAuthException.Reason.FLOW_EXPIRED,"expired outcome reason must replay");}}
        check(provider.polls.get()==1,"expired terminal outcome must prevent a second provider poll");
    }

    private static void refreshIsSingleFlightAndWaitersRemainBounded() throws Exception {
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);FakeProvider provider=new FakeProvider(Behavior.PENDING,null,null,entered,release);MemoryVault vault=new MemoryVault(false);Fixture f=fixture(provider,vault);f.auth.setRefreshLockWait(Duration.ofMillis(40));RemoteCredentialMaterial stale=credential("stale",Instant.now().plusSeconds(30));vault.seed("conn",stale);
        AtomicReference<RemoteCredentialMaterial> ownerValue=new AtomicReference<>();Thread owner=Thread.ofPlatform().start(()->ownerValue.set(f.service.credential("github",f.profile)));check(entered.await(2,TimeUnit.SECONDS),"one refresh owner must enter provider");
        long start=System.nanoTime();RemoteCredentialMaterial waiter=f.service.credential("github",f.profile);long elapsed=TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start);check("stale".equals(waiter.accessToken()),"waiter may safely reuse still-valid token while another refresh owns the lock");check(elapsed<750,"refresh waiter must remain bounded");check(provider.refreshes.get()==1,"waiter must not duplicate refresh");
        release.countDown();owner.join(3000);check(!owner.isAlive(),"refresh owner must terminate");check(ownerValue.get()!=null&&"refreshed".equals(ownerValue.get().accessToken()),"owner must publish refreshed credential");check(provider.refreshes.get()==1,"refresh must remain single-flight");check("refreshed".equals(f.service.credential("github",f.profile).accessToken()),"subsequent caller must see stored refreshed credential");check(provider.refreshes.get()==1,"fresh credential must suppress extra refresh");
    }

    private static void expiredRefreshWaiterFailsFast() throws Exception {
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);FakeProvider provider=new FakeProvider(Behavior.PENDING,null,null,entered,release);MemoryVault vault=new MemoryVault(false);Fixture f=fixture(provider,vault);f.auth.setRefreshLockWait(Duration.ofMillis(40));vault.seed("conn",credential("expired",Instant.now().minusSeconds(1)));
        AtomicReference<Throwable> ownerError=new AtomicReference<>();Thread owner=Thread.ofPlatform().start(()->{try{f.service.credential("github",f.profile);}catch(Throwable t){ownerError.set(t);}});check(entered.await(2,TimeUnit.SECONDS),"expired credential refresh owner must enter provider");long start=System.nanoTime();try{f.service.credential("github",f.profile);throw new AssertionError("expired waiter must not block behind refresh owner");}catch(RemoteAuthException e){check(e.getReason()==RemoteAuthException.Reason.REMOTE_ERROR,"expired waiter must receive retryable bounded-wait error");}long elapsed=TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start);check(elapsed<750,"expired waiter must fail within configured bound");check(provider.refreshes.get()==1,"expired waiter must not duplicate refresh");release.countDown();owner.join(3000);check(!owner.isAlive()&&ownerError.get()==null,"refresh owner must still finish");check(provider.refreshes.get()==1,"only owner performs provider refresh");
    }

    private static void interruptedRefreshWaiterPreservesCancellation() throws Exception {
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);FakeProvider provider=new FakeProvider(Behavior.PENDING,null,null,entered,release);MemoryVault vault=new MemoryVault(false);Fixture f=fixture(provider,vault);f.auth.setRefreshLockWait(Duration.ofSeconds(3));vault.seed("conn",credential("expired-int",Instant.now().minusSeconds(1)));
        Thread owner=Thread.ofPlatform().start(()->f.service.credential("github",f.profile));check(entered.await(2,TimeUnit.SECONDS),"refresh owner must hold lock before interruption test");AtomicReference<RemoteAuthException> error=new AtomicReference<>();AtomicBoolean preserved=new AtomicBoolean();Thread waiter=Thread.ofPlatform().start(()->{try{f.service.credential("github",f.profile);}catch(RemoteAuthException e){error.set(e);preserved.set(Thread.currentThread().isInterrupted());}});Thread.sleep(75);waiter.interrupt();waiter.join(2000);check(!waiter.isAlive(),"interrupted refresh waiter must finish promptly");check(error.get()!=null&&error.get().getReason()==RemoteAuthException.Reason.REMOTE_ERROR,"interrupted refresh waiter must receive stable cancellation error");check(preserved.get(),"refresh waiter must preserve interrupt flag");check(provider.refreshes.get()==1,"interrupted refresh waiter must not duplicate provider call");release.countDown();owner.join(3000);check(!owner.isAlive(),"refresh owner must terminate after release");
    }

    private static void boundedStoresRemainBounded(){
        FakeProvider provider=new FakeProvider(Behavior.PENDING,null,null);Fixture f=fixture(provider,new MemoryVault(false));f.auth.setMaximumPendingFlows(32);
        for(int i=0;i<5_000;i++)f.flows.put(flow("flow-"+i));check(f.flows.size()<=32,"flow store must remain bounded under churn");
        RemoteAuthException denied=new RemoteAuthException(RemoteAuthException.Reason.TOKEN_EXCHANGE_FAILED,"denied");for(int i=0;i<5_000;i++)f.outcomes.failure("outcome-"+i,"github",denied,Instant.now().plusSeconds(30));check(f.outcomes.size()<=32,"outcome store must remain bounded under churn");
    }

    private enum Behavior{AUTHORIZED,PENDING,DENIED}
    private static final class FakeProvider implements RemoteOAuthProvider{
        private final Behavior behavior;private final CountDownLatch pollEntered,pollRelease,refreshEntered,refreshRelease;private final AtomicInteger polls=new AtomicInteger(),refreshes=new AtomicInteger();
        private FakeProvider(Behavior behavior,CountDownLatch entered,CountDownLatch release){this(behavior,entered,release,null,null);}
        private FakeProvider(Behavior behavior,CountDownLatch pollEntered,CountDownLatch pollRelease,CountDownLatch refreshEntered,CountDownLatch refreshRelease){this.behavior=behavior;this.pollEntered=pollEntered;this.pollRelease=pollRelease;this.refreshEntered=refreshEntered;this.refreshRelease=refreshRelease;}
        public RemoteStorageProviderType type(){return RemoteStorageProviderType.GITHUB;}public List<String> scopes(RemoteStorageProperties.Profile s,RemoteAuthProperties.Provider a){return List.of("repo");}public boolean useDeviceFlow(RemoteStorageProperties.Profile s,RemoteAuthProperties.Provider a){return true;}
        public RemoteDeviceAuthorization beginDeviceAuthorization(RemoteStorageProperties.Profile s,RemoteAuthProperties.Provider a,List<String> scopes){return new RemoteDeviceAuthorization("device","user",URI.create("https://example.test/device"),Instant.now().plusSeconds(60),1);}public URI authorizationUri(RemoteStorageProperties.Profile s,RemoteAuthProperties.Provider a,String r,String state,String c,List<String> scopes){return URI.create("https://example.test/auth");}
        public RemoteCredentialMaterial exchange(RemoteStorageProperties.Profile s,RemoteAuthProperties.Provider a,String r,String code,String v,List<String> scopes){return credential();}
        public RemoteCredentialMaterial refresh(RemoteStorageProperties.Profile s,RemoteAuthProperties.Provider a,RemoteCredentialMaterial current){refreshes.incrementAndGet();if(refreshEntered!=null)refreshEntered.countDown();await(refreshRelease);return credential("refreshed",Instant.now().plusSeconds(3600));}
        public void revoke(RemoteStorageProperties.Profile s,RemoteAuthProperties.Provider a,RemoteCredentialMaterial current){}
        public RemoteDevicePollResult pollDeviceAuthorization(RemoteStorageProperties.Profile s,RemoteAuthProperties.Provider a,String deviceCode,List<String> scopes){polls.incrementAndGet();if(pollEntered!=null)pollEntered.countDown();await(pollRelease);return switch(behavior){case AUTHORIZED->RemoteDevicePollResult.authorized(credential());case PENDING->RemoteDevicePollResult.pending(2);case DENIED->RemoteDevicePollResult.denied();};}
        private static void await(CountDownLatch latch){if(latch!=null)try{latch.await(3,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}}
    }
    private static final class MemoryVault implements RemoteCredentialVault{
        private final boolean fail;private final AtomicInteger stores=new AtomicInteger();private final ConcurrentHashMap<String,RemoteCredentialMaterial> values=new ConcurrentHashMap<>();private MemoryVault(boolean fail){this.fail=fail;}private void seed(String id,RemoteCredentialMaterial material){values.put(id,material);}public Optional<RemoteCredentialMaterial> load(String id){return Optional.ofNullable(values.get(id));}public void store(String id,RemoteCredentialMaterial material){stores.incrementAndGet();if(fail)throw new RemoteAuthException(RemoteAuthException.Reason.VAULT_UNAVAILABLE,"vault blocked");values.put(id,material);}public void delete(String id){values.remove(id);}public List<String> connectionIds(){return List.copyOf(values.keySet());}public boolean persistent(){return true;}public String protectionMode(){return "TEST";}
    }
    private record Fixture(RemoteAuthProperties auth,RemoteStorageProperties.Profile profile,RemoteAuthFlowStore flows,RemoteAuthOutcomeStore outcomes,FakeProvider provider,MemoryVault vault,RemoteAuthService service){}
}
