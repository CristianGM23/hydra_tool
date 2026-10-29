package com.example.editor.portability.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.NoSuchFileException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

/** Política de reintento ante bloqueos transitorios de fichero (determinista: no duerme de verdad). */
class TransientFileAccessRetryTests {
    @Test
    void aTransientAccessDeniedIsRetriedWithGrowingPausesUntilItSucceeds() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        List<Long> pauses = new ArrayList<>();
        TransientFileAccessRetry.run(() -> { if (calls.incrementAndGet() < 5) throw new AccessDeniedException("locked"); }, 12, 20, 500, pauses::add);
        assertThat(calls).hasValue(5);
        assertThat(pauses).containsExactly(20L, 40L, 80L, 160L);
    }

    @Test
    void pausesAreCappedAndThePersistentLockIsPropagatedAfterTheAttemptsAreExhausted() {
        AtomicInteger calls = new AtomicInteger();
        List<Long> pauses = new ArrayList<>();
        assertThatThrownBy(() -> TransientFileAccessRetry.run(() -> { calls.incrementAndGet(); throw new AccessDeniedException("still locked"); }, 8, 20, 100, pauses::add))
                .isInstanceOf(AccessDeniedException.class).hasMessageContaining("still locked");
        assertThat(calls).hasValue(8);
        assertThat(pauses).hasSize(7).containsExactly(20L, 40L, 80L, 100L, 100L, 100L, 100L);
    }

    @Test
    void anyOtherFailureIsNotRetried() {
        AtomicInteger calls = new AtomicInteger();
        assertThatThrownBy(() -> TransientFileAccessRetry.run(() -> { calls.incrementAndGet(); throw new NoSuchFileException("missing"); }, 12, 20, 500, pause -> { }))
                .isInstanceOf(NoSuchFileException.class);
        assertThat(calls).hasValue(1);
        assertThatThrownBy(() -> TransientFileAccessRetry.run(() -> { throw new IOException("disk full"); }, 12, 20, 500, pause -> { }))
                .isInstanceOf(IOException.class).hasMessage("disk full");
    }

    @Test
    void anInterruptionStopsRetryingAndKeepsTheOriginalFailure() {
        AtomicInteger calls = new AtomicInteger();
        assertThatThrownBy(() -> TransientFileAccessRetry.run(() -> { calls.incrementAndGet(); throw new AccessDeniedException("locked"); }, 12, 20, 500,
                pause -> { throw new InterruptedException("stop"); }))
                .isInstanceOf(AccessDeniedException.class).hasSuppressedException(new InterruptedException("stop"));
        assertThat(calls).hasValue(1);
        assertThat(Thread.interrupted()).as("the interrupt flag is restored").isTrue();
    }
}
