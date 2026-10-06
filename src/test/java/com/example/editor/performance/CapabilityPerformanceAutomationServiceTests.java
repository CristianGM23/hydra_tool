package com.example.editor.performance;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class CapabilityPerformanceAutomationServiceTests {
    @TempDir Path temporary;

    @Test
    void discardsWarmupCalculatesPercentilesAndApprovesAStableBaseline() {
        CapabilityPerformanceProperties properties = new CapabilityPerformanceProperties(); properties.setStorageRoot(temporary.toString());
        var mapper = JsonMapper.builder().findAndAddModules().build();
        var service = new CapabilityPerformanceAutomationService(new CapabilityPerformanceService(properties, mapper), properties, mapper);
        CapabilityPerformanceBudget budget = new CapabilityPerformanceBudget("capability-center", 500, 100_000_000, 10, 2_000_000, 2_000);
        List<CapabilityPerformanceSample> samples = List.of(sample("warm", 900), sample("one", 100), sample("two", 110), sample("three", 105));

        CapabilityPerformanceAutomationReport report = service.run(new CapabilityPerformanceScenarioRequest(
                "readiness", "capability-center", 1, samples, budget, 20, true));

        assertThat(report.measuredSamples()).isEqualTo(3);
        assertThat(report.percentiles().renderP50()).isEqualTo(105);
        assertThat(report.percentiles().renderP95()).isEqualTo(110);
        assertThat(report.passed()).isTrue();
        CapabilityPerformanceBaseline baseline = service.baseline("readiness", "capability-center");
        assertThat(baseline.automationReportId()).isEqualTo(report.reportId());
        assertThat(baseline.sourceReportId()).isEqualTo(report.reportId());
    }

    private CapabilityPerformanceSample sample(String id, long render) {
        return new CapabilityPerformanceSample(id, "capability-center", Instant.now(), render, 10_000_000, 2, 50_000, 200,
                Map.of("readiness", 100L), Map.of("capability-center.js", 100_000L));
    }
}
