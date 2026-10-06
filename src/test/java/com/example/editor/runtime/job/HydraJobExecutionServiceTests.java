package com.example.editor.runtime.job;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.Executor;

import org.junit.jupiter.api.Test;

class HydraJobExecutionServiceTests {
    @Test void executesWorkThroughTheSharedRegistry() {
        HydraJobProperties properties = new HydraJobProperties();
        HydraJobOrchestrator orchestrator = new HydraJobOrchestrator(properties);
        Executor direct = Runnable::run;
        HydraJobExecutionService service = new HydraJobExecutionService(orchestrator, direct);

        HydraJobSnapshot submitted = service.submit("job-1", "TEST", "Test job", "corr-1", context -> {
            context.progress(40, "WORK", "Working");
        });

        assertThat(submitted.id()).isEqualTo("job-1");
        assertThat(orchestrator.find("job-1").status()).isEqualTo(HydraJobStatus.COMPLETED);
        assertThat(orchestrator.find("job-1").progress()).isEqualTo(100);
    }

    @Test void convertsWorkFailuresIntoFailedSnapshots() {
        HydraJobOrchestrator orchestrator = new HydraJobOrchestrator(new HydraJobProperties());
        HydraJobExecutionService service = new HydraJobExecutionService(orchestrator, Runnable::run);
        service.submit("job-2", "TEST", "Failing job", "", context -> { throw new IllegalStateException("boom"); });
        assertThat(orchestrator.find("job-2").status()).isEqualTo(HydraJobStatus.FAILED);
        assertThat(orchestrator.find("job-2").message()).contains("boom");
    }
}
