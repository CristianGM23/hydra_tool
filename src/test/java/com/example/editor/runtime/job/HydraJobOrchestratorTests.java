package com.example.editor.runtime.job;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

class HydraJobOrchestratorTests {
    @Test void exposesProgressAndCancellationThroughOneRegistry() {
        HydraJobProperties properties = new HydraJobProperties();
        AtomicBoolean cancelled = new AtomicBoolean();
        HydraJobOrchestrator jobs = new HydraJobOrchestrator(properties,
                Clock.fixed(Instant.parse("2026-10-01T17:00:00Z"), ZoneOffset.UTC));
        jobs.register("job-1", "TEST", "Example", "corr-1", () -> cancelled.set(true));
        jobs.update("job-1", HydraJobStatus.RUNNING, 40, "PARSING", "Reading input");
        assertThat(jobs.find("job-1").progress()).isEqualTo(40);
        assertThat(jobs.cancel("job-1").status()).isEqualTo(HydraJobStatus.CANCELLED);
        assertThat(cancelled).isTrue();
    }
    @Test void evictsTerminalJobsBeforeRejectingNewWork() {
        HydraJobProperties properties = new HydraJobProperties();
        properties.setMaximumRetained(16);
        HydraJobOrchestrator jobs = new HydraJobOrchestrator(properties,
                Clock.fixed(Instant.parse("2026-10-01T17:00:00Z"), ZoneOffset.UTC));
        for (int index = 0; index < 16; index++) {
            jobs.register("job-" + index, "TEST", "Job " + index, "corr", null);
        }
        jobs.complete("job-0", "done");
        jobs.register("job-16", "TEST", "replacement", "corr", null);
        assertThat(jobs.list()).hasSize(16).extracting(HydraJobSnapshot::id).contains("job-16").doesNotContain("job-0");
    }

}
