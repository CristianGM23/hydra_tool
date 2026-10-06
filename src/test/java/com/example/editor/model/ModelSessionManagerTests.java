package com.example.editor.model;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModelSessionManagerTests {

    @Test
    void allowsIndependentSessionsToProgressInParallel() throws Exception {
        ModelSessionManager manager = manager(8, Duration.ofMinutes(5), new AtomicLong());
        manager.register(session("model-1"));
        manager.register(session("model-2"));
        CountDownLatch entered = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        try(var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> manager.withSession("model-1", session -> awaitTogether(entered, release)));
            var second = executor.submit(() -> manager.withSession("model-2", session -> awaitTogether(entered, release)));
            assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
            release.countDown();
            assertThat(first.get(2, TimeUnit.SECONDS)).isEqualTo("ok");
            assertThat(second.get(2, TimeUnit.SECONDS)).isEqualTo("ok");
        }
    }

    @Test
    void serializesOperationsForTheSameSession() throws Exception {
        ModelSessionManager manager = manager(8, Duration.ofMinutes(5), new AtomicLong());
        manager.register(session("model-1"));
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maximum = new AtomicInteger();
        try(var executor = Executors.newFixedThreadPool(4)) {
            var tasks = java.util.stream.IntStream.range(0, 8).mapToObj(index -> executor.submit(() ->
                    manager.withSession("model-1", session -> {
                        int current = active.incrementAndGet();
                        maximum.accumulateAndGet(current, Math::max);
                        try { Thread.sleep(10); } catch(InterruptedException exception) { Thread.currentThread().interrupt(); }
                        active.decrementAndGet();
                        return current;
                    }))).toList();
            for(var task : tasks) task.get(2, TimeUnit.SECONDS);
        }
        assertThat(maximum).hasValue(1);
    }

    @Test
    void expiresIdleSessionsAndEnforcesCapacity() {
        AtomicLong clock = new AtomicLong();
        ModelSessionManager manager = manager(1, Duration.ofSeconds(1), clock);
        manager.register(session("model-1"));
        assertThatThrownBy(() -> manager.register(session("model-2")))
                .isInstanceOf(ModelDocumentException.class)
                .hasMessageContaining("Maximum number of active model sessions");
        clock.addAndGet(Duration.ofSeconds(2).toNanos());
        manager.register(session("model-2"));
        assertThat(manager.snapshot().activeSessions()).isEqualTo(1);
        assertThatThrownBy(() -> manager.withSession("model-1", session -> "unexpected"))
                .isInstanceOf(ModelDocumentException.class)
                .hasMessageContaining("was not found");
    }


    @Test
    void failedLongRunningOperationStillRefreshesSessionActivity() {
        AtomicLong clock = new AtomicLong();
        ModelSessionManager manager = manager(1, Duration.ofSeconds(1), clock);
        manager.register(session("model-1"));

        assertThatThrownBy(() -> manager.withSession("model-1", session -> {
            clock.addAndGet(Duration.ofSeconds(2).toNanos());
            throw new IllegalStateException("simulated operation failure");
        })).isInstanceOf(IllegalStateException.class);

        String result = manager.withSession("model-1", session -> "still-active");
        assertThat(result).isEqualTo("still-active");
    }

    private ModelSessionManager manager(int maximum, Duration timeout, AtomicLong clock) {
        ModelSessionProperties properties = new ModelSessionProperties();
        properties.setMaxActive(maximum);
        properties.setIdleTimeout(timeout);
        return new ModelSessionManager(properties, clock::get);
    }

    private ModelSession session(String id) {
        return new ModelSession(id, "metamodel", id + ".xmi", null, null);
    }

    private String awaitTogether(CountDownLatch entered, CountDownLatch release) {
        entered.countDown();
        try {
            if(!release.await(2, TimeUnit.SECONDS)) throw new AssertionError("parallel session did not enter");
        } catch(InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
        return "ok";
    }
}
