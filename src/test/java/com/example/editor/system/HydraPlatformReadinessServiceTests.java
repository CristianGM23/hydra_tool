package com.example.editor.system;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class HydraPlatformReadinessServiceTests {

    @Test
    void reportsReadyWhenRuntimeConfigurationAndCriticalContractsAreAvailable() {
        HydraPlatformReadinessService service = new HydraPlatformReadinessService(1, "data/release-gates", 200,
                128, Duration.ofHours(2), HydraPlatformReadinessService.class.getClassLoader());

        HydraPlatformReadinessReport report = service.evaluate();

        assertThat(report.ready()).isTrue();
        assertThat(report.status()).isEqualTo("READY");
        assertThat(report.product()).isEqualTo("HYDRA");
        assertThat(report.checks()).allMatch(check -> check.status() == HydraPlatformReadinessCheckStatus.PASS);
    }

    @Test
    void blocksReadinessWhenConfiguredJavaMinimumCannotBeMet() {
        HydraPlatformReadinessService service = new HydraPlatformReadinessService(999, "data/release-gates", 200,
                128, Duration.ofHours(2), HydraPlatformReadinessService.class.getClassLoader());

        HydraPlatformReadinessReport report = service.evaluate();

        assertThat(report.ready()).isFalse();
        assertThat(report.status()).isEqualTo("NOT_READY");
        assertThat(report.checks()).anyMatch(check -> check.code().equals("JAVA_RUNTIME")
                && check.status() == HydraPlatformReadinessCheckStatus.FAIL);
    }
}
