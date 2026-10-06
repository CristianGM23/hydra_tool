package com.example.editor.audit;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.example.editor.scope.ResourceScope;
import com.example.editor.scope.WorkspaceContext;

/** Stress ejecutable del ciclo de vida, backpressure y cierre acotado del publisher de auditoría. */
public final class DefaultAuditEventPublisherCompletionStressHarness {
    private static int checks;

    public static void main(String[] args) throws Exception {
        propertyBounds();
        normalConcurrentDrain();
        retryThenRecover();
        overflowRemainsBounded();
        blockingStoreCannotBlockShutdown();
        publishAfterDestroyIsRejected();
        concurrentDestroyIsIdempotent();
        System.out.println("HYDRA_AUDIT_COMPLETION_STRESS=" + checks + "/" + checks + " PASS");
    }

    private static void propertyBounds() {
        AuditLogProperties p = properties();
        p.setWriteRetries(10); check(p.getWriteRetries() == 10, "maximum retry bound accepted");
        expectIllegal(() -> p.setWriteRetries(11), "retry upper bound rejected");
        expectIllegal(() -> p.setWriteTimeoutMillis(99), "write timeout lower bound rejected");
        expectIllegal(() -> p.setWriteTimeoutMillis(120001), "write timeout upper bound rejected");
        expectIllegal(() -> p.setShutdownWaitMillis(99), "shutdown lower bound rejected");
        expectIllegal(() -> p.setShutdownFlushLockMillis(9), "flush-lock lower bound rejected");
    }

    private static void normalConcurrentDrain() throws Exception {
        AuditLogProperties p = properties();
        p.setQueueCapacity(8192); p.setBatchSize(128); p.setFlushIntervalMillis(10); p.setWriteRetries(2);
        MemoryStore store = new MemoryStore();
        DefaultAuditEventPublisher publisher = publisher(p, store);
        publisher.afterPropertiesSet();
        int producers = 12, each = 500, total = producers * each;
        ExecutorService pool = Executors.newFixedThreadPool(producers);
        CountDownLatch start = new CountDownLatch(1);
        for(int t = 0; t < producers; t++) {
            int base = t * each;
            pool.submit(() -> { await(start); for(int i = 0; i < each; i++) publisher.publish(event("normal-" + (base + i))); });
        }
        start.countDown(); pool.shutdown();
        check(pool.awaitTermination(10, TimeUnit.SECONDS), "all concurrent publishers terminate");
        waitUntil(() -> publisher.status().persisted() == total, Duration.ofSeconds(10));
        check(publisher.status().persisted() == total, "all concurrent audit events persist");
        check(publisher.status().failures() == 0, "normal concurrent drain has no failures");
        check(publisher.status().queued() == 0, "normal concurrent drain empties queue");
        publisher.destroy();
        check(!publisher.status().running(), "normal publisher is stopped");
        check(store.entries.size() == total, "memory store received every event exactly once");
    }

    private static void retryThenRecover() throws Exception {
        AuditLogProperties p = properties();
        p.setQueueCapacity(64); p.setBatchSize(8); p.setFlushIntervalMillis(10); p.setWriteRetries(2); p.setWriteTimeoutMillis(500);
        FlakyStore store = new FlakyStore(2);
        DefaultAuditEventPublisher publisher = publisher(p, store);
        publisher.afterPropertiesSet();
        for(int i = 0; i < 8; i++) publisher.publish(event("retry-" + i));
        waitUntil(() -> publisher.status().persisted() == 8, Duration.ofSeconds(5));
        check(store.attempts.get() >= 3, "storage retries were exercised");
        check(publisher.status().persisted() == 8, "retry path eventually persists batch");
        check(publisher.status().failures() == 0, "successful retry is not reported as terminal failure");
        publisher.destroy();
        check(publisher.status().queued() == 0, "retry publisher closes with empty queue");
    }

    private static void overflowRemainsBounded() throws Exception {
        AuditLogProperties p = properties();
        p.setQueueCapacity(4); p.setBatchSize(1); p.setFlushIntervalMillis(10); p.setWriteRetries(0); p.setWriteTimeoutMillis(120);
        p.setOverflowPolicy("DROP_NEWEST"); p.setDropLowValueOnOverflow(false);
        GateStore store = new GateStore();
        DefaultAuditEventPublisher publisher = publisher(p, store);
        publisher.afterPropertiesSet();
        publisher.publish(event("overflow-head"));
        check(store.entered.await(2, TimeUnit.SECONDS), "slow store is entered");
        long started = System.nanoTime();
        for(int i = 0; i < 5000; i++) publisher.publish(event("overflow-" + i));
        long millis = elapsedMillis(started);
        check(millis < 1500, "5000 overflow publishes remain non-blocking");
        check(publisher.status().queued() <= p.getQueueCapacity(), "audit queue never exceeds configured capacity");
        check(publisher.status().dropped() > 0, "overflow policy accounts dropped events");
        store.release.countDown();
        waitUntil(() -> publisher.status().queued() == 0, Duration.ofSeconds(3));
        publisher.destroy();
        check(publisher.status().queued() == 0, "overflow shutdown leaves no queued events");
    }

    private static void blockingStoreCannotBlockShutdown() throws Exception {
        AuditLogProperties p = properties();
        p.setQueueCapacity(32); p.setBatchSize(1); p.setFlushIntervalMillis(10); p.setWriteRetries(0);
        p.setWriteTimeoutMillis(100); p.setShutdownWaitMillis(100); p.setShutdownFlushLockMillis(20);
        p.setOverflowPolicy("DROP_NEWEST"); p.setDropLowValueOnOverflow(false);
        UncooperativeStore store = new UncooperativeStore();
        DefaultAuditEventPublisher publisher = publisher(p, store);
        publisher.afterPropertiesSet();
        publisher.publish(event("blocked-head"));
        check(store.entered.await(2, TimeUnit.SECONDS), "uncooperative store is entered");
        for(int i = 0; i < 30; i++) publisher.publish(event("blocked-" + i));
        long started = System.nanoTime();
        publisher.destroy();
        long millis = elapsedMillis(started);
        check(millis < 900, "destroy remains bounded when storage ignores interruption");
        check(!publisher.status().running(), "blocked publisher reaches terminal stopped state");
        check(publisher.status().queued() == 0, "blocked shutdown accounts and clears queued work");
        check(publisher.status().failures() > 0 || publisher.status().dropped() > 0, "blocked storage is observable as failure or drop");
        store.release.countDown();
        waitUntil(() -> !threadAlive("hydra-audit-storage-writer"), Duration.ofSeconds(2));
        check(!threadAlive("hydra-audit-writer"), "logical audit worker is not leaked");
    }

    private static void publishAfterDestroyIsRejected() {
        AuditLogProperties p = properties();
        MemoryStore store = new MemoryStore();
        DefaultAuditEventPublisher publisher = publisher(p, store);
        publisher.afterPropertiesSet(); publisher.destroy();
        long accepted = publisher.status().accepted(), dropped = publisher.status().dropped();
        publisher.publish(event("after-close"));
        check(publisher.status().accepted() == accepted, "publish after destroy is not accepted");
        check(publisher.status().dropped() == dropped + 1, "publish after destroy is accounted as dropped");
        check(publisher.status().queued() == 0, "publish after destroy cannot resurrect queue");
        publisher.afterPropertiesSet();
        check(!publisher.status().running(), "destroyed publisher cannot be restarted");
    }

    private static void concurrentDestroyIsIdempotent() throws Exception {
        AuditLogProperties p = properties();
        p.setQueueCapacity(1024); p.setBatchSize(32); p.setFlushIntervalMillis(10);
        DefaultAuditEventPublisher publisher = publisher(p, new MemoryStore());
        publisher.afterPropertiesSet();
        for(int i = 0; i < 500; i++) publisher.publish(event("close-race-" + i));
        ExecutorService pool = Executors.newFixedThreadPool(16);
        for(int i = 0; i < 64; i++) pool.submit(publisher::destroy);
        pool.shutdown();
        check(pool.awaitTermination(5, TimeUnit.SECONDS), "concurrent destroy callers all terminate");
        check(!publisher.status().running(), "concurrent destroy leaves publisher stopped");
        check(publisher.status().queued() == 0, "concurrent destroy leaves no queue backlog");
        check(!threadAlive("hydra-audit-writer"), "concurrent destroy leaves no audit worker");
    }

    private static DefaultAuditEventPublisher publisher(AuditLogProperties p, AuditLogStore store) {
        AuditActorContext actor = () -> new AuditActor("tester", "Tester", "session", java.util.Map.of());
        WorkspaceContext workspace = ResourceScope::local;
        return new DefaultAuditEventPublisher(p, actor, workspace, new AuditSnapshotService(), new AuditDiffStrategyRegistry(), store);
    }

    private static AuditLogProperties properties() {
        AuditLogProperties p = new AuditLogProperties();
        p.setQueueCapacity(256); p.setBatchSize(32); p.setFlushIntervalMillis(20); p.setWriteRetries(1);
        p.setWriteTimeoutMillis(500); p.setShutdownWaitMillis(300); p.setShutdownFlushLockMillis(50);
        p.setDropLowValueOnOverflow(false);
        return p;
    }

    private static AuditEvent event(String id) {
        return AuditEvent.builder("model.update", "model", AuditActions.UPDATE, new AuditResource("model", "Element", id, id))
                .eventId(id).metadata("payload", "x".repeat(128)).build();
    }

    private static void waitUntil(Check condition, Duration timeout) throws Exception {
        long deadline = System.nanoTime() + timeout.toNanos();
        while(System.nanoTime() < deadline) { if(condition.ok()) return; Thread.sleep(5); }
    }

    private static boolean threadAlive(String name) {
        return Thread.getAllStackTraces().keySet().stream().anyMatch(t -> t.isAlive() && name.equals(t.getName()));
    }

    private static long elapsedMillis(long started) { return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started); }
    private static void await(CountDownLatch latch) { try { latch.await(); } catch(InterruptedException e) { Thread.currentThread().interrupt(); throw new RuntimeException(e); } }
    private static void expectIllegal(Runnable action, String label) { try { action.run(); throw new AssertionError(label); } catch(IllegalArgumentException expected) { check(true, label); } }
    private static void check(boolean condition, String label) { if(!condition) throw new AssertionError(label); checks++; }
    @FunctionalInterface private interface Check { boolean ok() throws Exception; }

    private static class MemoryStore implements AuditLogStore {
        final List<AuditEntry> entries = Collections.synchronizedList(new ArrayList<>());
        @Override public void appendBatch(List<AuditEntry> batch) { entries.addAll(batch); }
        @Override public AuditPage query(AuditQuery query) { return null; }
        @Override public AuditStatistics statistics(AuditQuery query) { return null; }
        @Override public long stream(AuditQuery query, AuditEntrySink sink, long maximumEntries) { return 0; }
        @Override public void purgeBefore(Instant cutoff) { }
    }
    private static final class FlakyStore extends MemoryStore {
        final AtomicInteger attempts = new AtomicInteger(); final int failures;
        FlakyStore(int failures) { this.failures = failures; }
        @Override public void appendBatch(List<AuditEntry> batch) { if(attempts.getAndIncrement() < failures) throw new AuditStorageException("planned", null); super.appendBatch(batch); }
    }
    private static class GateStore extends MemoryStore {
        final CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        @Override public void appendBatch(List<AuditEntry> batch) { entered.countDown(); await(release); super.appendBatch(batch); }
    }
    private static final class UncooperativeStore extends MemoryStore {
        final CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        @Override public void appendBatch(List<AuditEntry> batch) {
            entered.countDown();
            while(release.getCount() > 0) { try { release.await(); } catch(InterruptedException ignored) { } }
            super.appendBatch(batch);
        }
    }
}
