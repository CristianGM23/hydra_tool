import com.example.editor.concurrent.BoundedPermitPool;
import com.example.editor.grammar.GrammarProperties;
import com.example.editor.grammar.application.GrammarAnalysisCache;
import com.example.editor.grammar.application.GrammarAnalysisCoordinator;
import com.example.editor.grammar.application.GrammarToolchainProfileService;
import com.example.editor.grammar.core.*;
import com.example.editor.grammar.provider.*;
import com.example.editor.grammar.runtime.GrammarProviderHealthService;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Aggressive executable completion/concurrency harness for GrammarAnalysisCoordinator. */
public final class GrammarAnalysisCoordinatorCompletionStressHarness {
    private static int checks;

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private enum Behavior { SUCCESS, FAIL, BLOCK_INTERRUPTIBLE, BLOCK_IGNORE_INTERRUPT }

    private static final class Profiles extends GrammarToolchainProfileService {
        private final GrammarToolchainProfile profile;
        Profiles(long timeoutMillis) {
            profile = new GrammarToolchainProfile("stress", GrammarTechnology.LANGIUM, "stress", "stress", "", "20+", "none", "none",
                    false, timeoutMillis, 1_000, 64L * 1024 * 1024, GrammarExecutionMode.INTERNAL, Map.of());
        }
        @Override public GrammarToolchainProfile require(String id, GrammarTechnology technology) { return profile; }
    }

    private static final class Provider implements GrammarProvider {
        private final GrammarProviderDescriptor descriptor = new GrammarProviderDescriptor(
                "stress-provider", GrammarTechnology.LANGIUM, "Stress", "1", List.of(), List.of("FAST", "NORMAL", "FULL"), Map.of());
        private final ConcurrentMap<String, Behavior> behavior = new ConcurrentHashMap<>();
        private final ConcurrentMap<String, CountDownLatch> entered = new ConcurrentHashMap<>();
        private final ConcurrentMap<String, CountDownLatch> release = new ConcurrentHashMap<>();
        private final AtomicInteger invocations = new AtomicInteger();
        private final AtomicInteger interruptions = new AtomicInteger();
        private final AtomicInteger completions = new AtomicInteger();

        void behavior(String requestId, Behavior value) { behavior.put(requestId, value); }
        boolean awaitEntered(String requestId, long timeoutMillis) throws InterruptedException {
            return entered.computeIfAbsent(requestId, ignored -> new CountDownLatch(1)).await(timeoutMillis, TimeUnit.MILLISECONDS);
        }
        void release(String requestId) { release.computeIfAbsent(requestId, ignored -> new CountDownLatch(1)).countDown(); }
        int invocations() { return invocations.get(); }
        int interruptions() { return interruptions.get(); }
        int completions() { return completions.get(); }

        @Override public GrammarProviderDescriptor descriptor() { return descriptor; }
        @Override public GrammarAnalysisResult analyze(GrammarDefinitionModel grammar, GrammarAnalysisRequest request) {
            invocations.incrementAndGet();
            entered.computeIfAbsent(request.requestId(), ignored -> new CountDownLatch(1)).countDown();
            Behavior mode = behavior.getOrDefault(request.requestId(), Behavior.SUCCESS);
            if (mode == Behavior.FAIL) throw new IllegalStateException("synthetic-provider-failure");
            if (mode == Behavior.BLOCK_INTERRUPTIBLE) {
                try {
                    release.computeIfAbsent(request.requestId(), ignored -> new CountDownLatch(1)).await();
                } catch (InterruptedException exception) {
                    interruptions.incrementAndGet();
                    Thread.currentThread().interrupt();
                    throw new CancellationException("provider interrupted");
                }
            } else if (mode == Behavior.BLOCK_IGNORE_INTERRUPT) {
                long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(900);
                while (System.nanoTime() < deadline) {
                    try { Thread.sleep(20); }
                    catch (InterruptedException exception) { interruptions.incrementAndGet(); }
                }
            }
            completions.incrementAndGet();
            return success(grammar, request);
        }
        private GrammarAnalysisResult success(GrammarDefinitionModel grammar, GrammarAnalysisRequest request) {
            return new GrammarAnalysisResult(request.requestId(), request.draftRevision(), request.sourceHash(), request.mode(),
                    "stress-provider:stress", false, List.of(), List.of(), grammar.semanticBindings(),
                    ProviderHealth.healthy("stress-provider:stress"), List.of(), Map.of("providerResult", "true"));
        }
        @Override public GeneratedGrammarProject generateProject(GrammarProjectGenerationRequest request) { return null; }
        @Override public GrammarRuntimeDescriptor runtimeDescriptor(GrammarDefinitionModel grammar, GrammarToolchainProfile profile) { return null; }
        @Override public GrammarSyntaxMetadata syntaxMetadata() { return null; }
    }

    private static GrammarDefinitionModel grammar() {
        GrammarSourceDocument doc = new GrammarSourceDocument("doc", "main.langium", "langium", "entry Model: name=ID;", "", true, false);
        return new GrammarDefinitionModel("1.0", "grammar-stress", "Stress", GrammarTechnology.LANGIUM, GrammarAuthoringMode.GRAMMAR_FIRST,
                "", "stress", doc.id(), "Model", List.of(doc), GrammarSemanticBindingMap.empty(), "", Map.of(), Map.of());
    }

    private static GrammarAnalysisRequest request(GrammarDefinitionModel grammar, String id, long revision) {
        return new GrammarAnalysisRequest(id, grammar.id(), grammar.entryDocumentId(), revision, GrammarAnalysisMode.FAST,
                List.of(grammar.entryDocumentId()), grammar.documents(), "stress", "", Map.of());
    }

    private static GrammarAnalysisCoordinator coordinator(Provider provider, GrammarProperties properties, long timeoutMillis) {
        return new GrammarAnalysisCoordinator(new Profiles(timeoutMillis), new GrammarProviderRegistry(List.of(provider)),
                new GrammarAnalysisCache(properties), new GrammarProviderHealthService(properties), properties);
    }

    private static void awaitCondition(Callable<Boolean> condition, long timeoutMillis, String message) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        while (System.nanoTime() < deadline) {
            if (condition.call()) return;
            Thread.sleep(5);
        }
        check(condition.call(), message);
    }

    private static void successAndCache() throws Exception {
        Provider provider = new Provider();
        GrammarProperties props = new GrammarProperties();
        props.setMaximumConcurrentAnalyses(2);
        try (GrammarAnalysisCoordinator coordinator = coordinator(provider, props, 500)) {
            GrammarDefinitionModel grammar = grammar();
            GrammarAnalysisResult first = coordinator.analyze(grammar, request(grammar, "success-1", 1));
            check(!first.stale(), "success result must not be stale");
            check("true".equals(first.metadata().get("providerResult")), "provider result must be preserved");
            check(provider.invocations() == 1, "provider invoked once");
            GrammarAnalysisResult cached = coordinator.analyze(grammar, request(grammar, "success-2", 1));
            check("true".equals(cached.metadata().get("cacheHit")), "second result must be a cache hit");
            check(provider.invocations() == 1, "cache must avoid second provider invocation");
            BoundedPermitPool.Snapshot snapshot = coordinator.capacityStatus();
            check(snapshot.inUse() == 0 && snapshot.queued() == 0, "capacity must be fully recovered");
        }
    }

    private static void timeoutCancelsProvider() throws Exception {
        Provider provider = new Provider();
        GrammarProperties props = new GrammarProperties();
        props.setMaximumConcurrentAnalyses(1);
        provider.behavior("timeout", Behavior.BLOCK_INTERRUPTIBLE);
        long start = System.nanoTime();
        try (GrammarAnalysisCoordinator coordinator = coordinator(provider, props, 250)) {
            GrammarDefinitionModel grammar = grammar();
            GrammarAnalysisResult result = coordinator.analyze(grammar, request(grammar, "timeout", 1));
            long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            check("TIMEOUT".equals(result.metadata().get("degradedReason")), "timeout must degrade explicitly");
            check(elapsed < 2_000, "timeout must remain bounded");
            awaitCondition(() -> provider.interruptions() >= 1, 800, "provider must observe timeout interruption");
            check(coordinator.capacityStatus().inUse() == 0, "timeout must release permit");
        }
    }

    private static void explicitCancellation() throws Exception {
        Provider provider = new Provider();
        GrammarProperties props = new GrammarProperties();
        props.setMaximumConcurrentAnalyses(1);
        provider.behavior("cancel", Behavior.BLOCK_INTERRUPTIBLE);
        try (GrammarAnalysisCoordinator coordinator = coordinator(provider, props, 2_000)) {
            GrammarDefinitionModel grammar = grammar();
            AtomicReference<GrammarAnalysisResult> result = new AtomicReference<>();
            AtomicBoolean callerInterrupted = new AtomicBoolean();
            Thread caller = new Thread(() -> {
                result.set(coordinator.analyze(grammar, request(grammar, "cancel", 1)));
                callerInterrupted.set(Thread.currentThread().isInterrupted());
            }, "grammar-cancel-caller");
            caller.start();
            check(provider.awaitEntered("cancel", 1_000), "provider must start before cancellation");
            check(coordinator.cancel("cancel"), "cancel must locate active request");
            caller.join(1_500);
            check(!caller.isAlive(), "caller must finish after cancellation");
            check("CANCELLED".equals(result.get().metadata().get("cancelReason")), "cancel reason must be observable");
            check(!callerInterrupted.get(), "Future cancellation must not poison caller interrupt flag");
            awaitCondition(() -> provider.interruptions() >= 1, 800, "provider must observe explicit cancellation");
            check(coordinator.capacityStatus().inUse() == 0, "cancellation must release permit");
        }
    }

    private static void callerInterruptionCancelsProvider() throws Exception {
        Provider provider = new Provider();
        GrammarProperties props = new GrammarProperties();
        props.setMaximumConcurrentAnalyses(1);
        provider.behavior("caller-interrupt", Behavior.BLOCK_INTERRUPTIBLE);
        try (GrammarAnalysisCoordinator coordinator = coordinator(provider, props, 2_000)) {
            GrammarDefinitionModel grammar = grammar();
            AtomicReference<GrammarAnalysisResult> result = new AtomicReference<>();
            AtomicBoolean preserved = new AtomicBoolean();
            Thread caller = new Thread(() -> {
                result.set(coordinator.analyze(grammar, request(grammar, "caller-interrupt", 1)));
                preserved.set(Thread.currentThread().isInterrupted());
            }, "grammar-interrupted-caller");
            caller.start();
            check(provider.awaitEntered("caller-interrupt", 1_000), "provider must start before caller interruption");
            caller.interrupt();
            caller.join(1_500);
            check(!caller.isAlive(), "interrupted caller must terminate");
            check("CALLER_INTERRUPTED".equals(result.get().metadata().get("cancelReason")), "caller interruption must be explicit");
            check(preserved.get(), "caller interrupt flag must be preserved");
            awaitCondition(() -> provider.interruptions() >= 1, 800, "caller interruption must cancel provider Future");
            check(coordinator.capacityStatus().inUse() == 0, "caller interruption must release permit");
        }
    }

    private static void duplicateRequestIdDoesNotOverwrite() throws Exception {
        Provider provider = new Provider();
        GrammarProperties props = new GrammarProperties();
        props.setMaximumConcurrentAnalyses(2);
        provider.behavior("duplicate", Behavior.BLOCK_INTERRUPTIBLE);
        try (GrammarAnalysisCoordinator coordinator = coordinator(provider, props, 2_000)) {
            GrammarDefinitionModel grammar = grammar();
            AtomicReference<GrammarAnalysisResult> first = new AtomicReference<>();
            Thread caller = new Thread(() -> first.set(coordinator.analyze(grammar, request(grammar, "duplicate", 1))));
            caller.start();
            check(provider.awaitEntered("duplicate", 1_000), "first duplicate request must start");
            GrammarAnalysisResult second = coordinator.analyze(grammar, request(grammar, "duplicate", 2));
            check("DUPLICATE_REQUEST_ID".equals(second.metadata().get("cancelReason")), "duplicate id must be rejected explicitly");
            check(provider.invocations() == 1, "duplicate id must not invoke a second provider task");
            check(coordinator.cancel("duplicate"), "original request must remain cancellable");
            caller.join(1_500);
            check(!caller.isAlive(), "original duplicate caller must finish after cancel");
            check(coordinator.capacityStatus().inUse() == 0, "duplicate rejection must not leak permits");
        }
    }

    private static void staleSuppression() throws Exception {
        Provider provider = new Provider();
        GrammarProperties props = new GrammarProperties();
        props.setMaximumConcurrentAnalyses(2);
        provider.behavior("old", Behavior.BLOCK_INTERRUPTIBLE);
        try (GrammarAnalysisCoordinator coordinator = coordinator(provider, props, 2_000)) {
            GrammarDefinitionModel grammar = grammar();
            AtomicReference<GrammarAnalysisResult> old = new AtomicReference<>();
            Thread caller = new Thread(() -> old.set(coordinator.analyze(grammar, request(grammar, "old", 1))));
            caller.start();
            check(provider.awaitEntered("old", 1_000), "old revision must start");
            GrammarAnalysisResult newer = coordinator.analyze(grammar, request(grammar, "new", 2));
            check(!newer.stale(), "newer revision must be current");
            provider.release("old");
            caller.join(1_500);
            check(!caller.isAlive(), "old revision must finish");
            check(old.get().stale(), "old revision must be marked stale");
            check(coordinator.capacityStatus().inUse() == 0, "stale completion must release permits");
        }
    }

    private static void boundedExecutorUnderNonCooperativeProvider() throws Exception {
        Provider provider = new Provider();
        GrammarProperties props = new GrammarProperties();
        props.setMaximumConcurrentAnalyses(2);
        props.setMaximumQueuedAnalyses(0);
        props.setPermitAcquireTimeoutMillis(50);
        provider.behavior("stuck-a", Behavior.BLOCK_IGNORE_INTERRUPT);
        provider.behavior("stuck-b", Behavior.BLOCK_IGNORE_INTERRUPT);
        GrammarAnalysisCoordinator coordinator = coordinator(provider, props, 5_000);
        try {
            GrammarDefinitionModel grammar = grammar();
            AtomicReference<GrammarAnalysisResult> a = new AtomicReference<>();
            AtomicReference<GrammarAnalysisResult> b = new AtomicReference<>();
            Thread ta = new Thread(() -> a.set(coordinator.analyze(grammar, request(grammar, "stuck-a", 1))));
            Thread tb = new Thread(() -> b.set(coordinator.analyze(grammar, request(grammar, "stuck-b", 1))));
            ta.start(); tb.start();
            check(provider.awaitEntered("stuck-a", 1_000), "first non-cooperative task must start");
            check(provider.awaitEntered("stuck-b", 1_000), "second non-cooperative task must start");
            check(coordinator.cancel("stuck-a"), "cancel stuck-a");
            check(coordinator.cancel("stuck-b"), "cancel stuck-b");
            ta.join(700); tb.join(700);
            check(!ta.isAlive() && !tb.isAlive(), "callers must finish even when providers ignore interruption");
            long before = System.nanoTime();
            GrammarAnalysisResult saturated = coordinator.analyze(grammar, request(grammar, "saturation-probe", 2));
            long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - before);
            check("EXECUTOR_SATURATED".equals(saturated.metadata().get("degradedReason")), "executor saturation must be explicit");
            check(elapsed < 700, "saturation fallback must not queue behind stuck providers");
            Thread.sleep(1_000);
            GrammarAnalysisResult recovered = coordinator.analyze(grammar, request(grammar, "recovered", 3));
            check("true".equals(recovered.metadata().get("providerResult")), "executor must recover after stuck providers exit");
            check(coordinator.capacityStatus().queued() == 0, "permit queue must be empty after recovery");
        } finally {
            coordinator.close();
        }
    }

    private static void closeRacesAndPostClose() throws Exception {
        GrammarDefinitionModel grammar = grammar();
        for (int i = 0; i < 80; i++) {
            Provider provider = new Provider();
            GrammarProperties props = new GrammarProperties();
            props.setMaximumConcurrentAnalyses(1);
            props.setMaximumQueuedAnalyses(0);
            props.setAnalysisShutdownWaitMillis(100);
            String id = "close-race-" + i;
            provider.behavior(id, Behavior.BLOCK_INTERRUPTIBLE);
            GrammarAnalysisCoordinator coordinator = coordinator(provider, props, 2_000);
            AtomicReference<GrammarAnalysisResult> result = new AtomicReference<>();
            long revision = i + 1L;
            Thread caller = new Thread(() -> result.set(coordinator.analyze(grammar, request(grammar, id, revision))));
            caller.start();
            if ((i & 1) == 0) Thread.yield();
            coordinator.close();
            coordinator.close();
            caller.join(1_000);
            check(!caller.isAlive(), "close race caller must finish: " + i);
            check(result.get() != null && "true".equals(result.get().metadata().get("cancelled")), "close race must return cancelled: " + i);
            check(coordinator.capacityStatus().inUse() == 0, "close race must recover permit: " + i);
            long start = System.nanoTime();
            GrammarAnalysisResult after = coordinator.analyze(grammar, request(grammar, "after-close-" + i, 1000 + i));
            long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            check("true".equals(after.metadata().get("cancelled")), "post-close analysis must be terminal");
            check(elapsed < 250, "post-close analysis must return immediately");
        }
    }

    private static void concurrentBurst() throws Exception {
        Provider provider = new Provider();
        GrammarProperties props = new GrammarProperties();
        props.setMaximumConcurrentAnalyses(8);
        props.setMaximumQueuedAnalyses(16);
        props.setPermitAcquireTimeoutMillis(2_000);
        try (GrammarAnalysisCoordinator coordinator = coordinator(provider, props, 2_000)) {
            GrammarDefinitionModel grammar = grammar();
            int count = 240;
            ExecutorService callers = Executors.newFixedThreadPool(32);
            List<Future<GrammarAnalysisResult>> futures = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                final int index = i;
                futures.add(callers.submit(() -> coordinator.analyze(grammar, request(grammar, "burst-" + index, index + 1L))));
            }
            for (Future<GrammarAnalysisResult> future : futures) {
                GrammarAnalysisResult result = future.get(5, TimeUnit.SECONDS);
                check(result != null, "burst result must be present");
                check(!"true".equals(result.metadata().get("cancelled")), "normal burst must not cancel");
            }
            callers.shutdownNow();
            callers.awaitTermination(1, TimeUnit.SECONDS);
            BoundedPermitPool.Snapshot snapshot = coordinator.capacityStatus();
            check(snapshot.inUse() == 0, "burst must recover every permit");
            check(snapshot.queued() == 0, "burst must leave no waiting callers");
        }
    }

    public static void main(String[] args) throws Exception {
        successAndCache();
        timeoutCancelsProvider();
        explicitCancellation();
        callerInterruptionCancelsProvider();
        duplicateRequestIdDoesNotOverwrite();
        staleSuppression();
        boundedExecutorUnderNonCooperativeProvider();
        closeRacesAndPostClose();
        concurrentBurst();
        System.out.println("GRAMMAR_ANALYSIS_COMPLETION_STRESS=" + checks + "/" + checks + " PASS");
    }
}
