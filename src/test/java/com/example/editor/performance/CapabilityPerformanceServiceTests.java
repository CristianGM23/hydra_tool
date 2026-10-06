package com.example.editor.performance;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class CapabilityPerformanceServiceTests {
    @TempDir Path temporary;

    @Test
    void evaluatesPersistsAndExportsComparableJsonAndHtmlReports() {
        CapabilityPerformanceProperties properties = new CapabilityPerformanceProperties();
        properties.setStorageRoot(temporary.toString());
        CapabilityPerformanceService service = new CapabilityPerformanceService(properties,
                JsonMapper.builder().findAndAddModules().build());
        CapabilityPerformanceSample sample = new CapabilityPerformanceSample("run-001",
                "update-site-import-center", Instant.parse("2026-08-04T12:00:00Z"), 1_500,
                300_000_000L, 30, 20_000_000L, 35_000,
                Map.of("repository-read", 500L, "bundle-analysis", 12_000L),
                Map.of("update-site-import-center.js", 1_200_000L));

        CapabilityPerformanceReport report = service.evaluate(sample);
        byte[] html = service.html(report.reportId());

        assertThat(report.evaluation().passed()).isFalse();
        assertThat(report.evaluation().violations()).hasSize(5);
        assertThat(report.evaluation().recommendations())
                .anyMatch(value -> value.contains("slowest stage is bundle-analysis"))
                .anyMatch(value -> value.contains("Split chunk update-site-import-center.js"));
        assertThat(service.reports()).singleElement().extracting(CapabilityPerformanceReport::reportId)
                .isEqualTo("run-001");
        assertThat(service.find("run-001")).isEqualTo(report);
        assertThat(new String(html, StandardCharsets.UTF_8))
                .contains("HYDRA capability performance", "Budget exceeded", "bundle-analysis");
        assertThat(Files.isRegularFile(temporary.resolve("run-001.json"))).isTrue();
    }

    @Test
    void normalizesUnsafeMeasurementsAndRetainsTheNewestReports() throws Exception {
        CapabilityPerformanceProperties properties = new CapabilityPerformanceProperties();
        properties.setStorageRoot(temporary.toString());
        properties.setMaximumReports(2);
        CapabilityPerformanceService service = new CapabilityPerformanceService(properties,
                JsonMapper.builder().findAndAddModules().build());

        CapabilityPerformanceReport first = service.evaluate(new CapabilityPerformanceSample("z-old",
                "capability-center", Instant.parse("2026-08-04T10:00:00Z"), -1, -2, -3, -4, -5,
                Map.of("readiness", -6L), Map.of("capability-center.js", -7L)));
        Files.setLastModifiedTime(temporary.resolve("z-old.json"), FileTime.fromMillis(1_000L));
        CapabilityPerformanceReport second = service.evaluate(new CapabilityPerformanceSample("a-middle",
                "capability-center", Instant.parse("2026-08-04T11:00:00Z"), 1, 2, 3, 4, 5, Map.of(), Map.of()));
        Files.setLastModifiedTime(temporary.resolve("a-middle.json"), FileTime.fromMillis(2_000L));
        CapabilityPerformanceReport third = service.evaluate(new CapabilityPerformanceSample("m-new",
                "capability-center", Instant.parse("2026-08-04T12:00:00Z"), 1, 2, 3, 4, 5, Map.of(), Map.of()));

        assertThat(first.sample().renderMillis()).isZero();
        assertThat(first.sample().stageMillis()).containsEntry("readiness", 0L);
        assertThat(service.reports()).extracting(CapabilityPerformanceReport::reportId)
                .containsExactly(third.reportId(), second.reportId());
        assertThat(temporary.resolve("z-old.json")).doesNotExist();
    }

    @Test
    void skipsCorruptReportsBeforeApplyingTheResponseLimit() throws Exception {
        CapabilityPerformanceProperties properties = new CapabilityPerformanceProperties();
        properties.setStorageRoot(temporary.toString());
        properties.setMaximumReports(1);
        CapabilityPerformanceService service = new CapabilityPerformanceService(properties,
                JsonMapper.builder().findAndAddModules().build());
        CapabilityPerformanceReport valid = service.evaluate(new CapabilityPerformanceSample("valid-report",
                "capability-center", Instant.parse("2026-08-04T12:00:00Z"), 10, 20, 1, 30, 40, Map.of(), Map.of()));
        Path corrupt = temporary.resolve("newer-corrupt.json");
        Files.writeString(corrupt, "{not-json", StandardCharsets.UTF_8);
        Files.setLastModifiedTime(corrupt, FileTime.fromMillis(9_000L));
        Files.setLastModifiedTime(temporary.resolve("valid-report.json"), FileTime.fromMillis(8_000L));

        assertThat(service.reports()).singleElement().extracting(CapabilityPerformanceReport::reportId)
                .isEqualTo(valid.reportId());
    }

    @Test
    void reportsMeasurementsWithinTheBudgetWithoutFalseViolations() {
        CapabilityPerformanceProperties properties = new CapabilityPerformanceProperties();
        properties.setStorageRoot(temporary.toString());
        CapabilityPerformanceService service = new CapabilityPerformanceService(properties,
                JsonMapper.builder().findAndAddModules().build());

        CapabilityPerformanceReport report = service.evaluate(new CapabilityPerformanceSample("run-ok",
                "capability-center", Instant.now(), 100, 10_000_000, 2, 20_000, 50,
                Map.of("readiness", 50L), Map.of("capability-center.js", 100_000L)));

        assertThat(report.evaluation().passed()).isTrue();
        assertThat(report.evaluation().violations()).isEmpty();
        assertThat(report.evaluation().recommendations())
                .containsExactly("All measured values are within the configured capability budget.");
    }
    @Test
    void comparesReportsAndFlagsMaterialRegressions() {
        CapabilityPerformanceProperties properties = new CapabilityPerformanceProperties();
        properties.setStorageRoot(temporary.toString());
        CapabilityPerformanceService service = new CapabilityPerformanceService(properties,
                JsonMapper.builder().findAndAddModules().build());
        service.evaluate(new CapabilityPerformanceSample("baseline", "update-site-import-center", Instant.now(),
                100, 20_000_000, 2, 100_000, 1_000, Map.of("analysis", 500L),
                Map.of("update-site-import-center.js", 100_000L)));
        service.evaluate(new CapabilityPerformanceSample("candidate", "update-site-import-center", Instant.now(),
                140, 25_000_000, 3, 500_000, 1_400, Map.of("analysis", 900L),
                Map.of("update-site-import-center.js", 300_000L)));

        CapabilityPerformanceComparison comparison = service.compare("baseline", "candidate");

        assertThat(comparison.regression()).isTrue();
        assertThat(comparison.renderDelta()).isEqualTo(40);
        assertThat(comparison.stageDeltas()).containsEntry("analysis", 400L);
        assertThat(comparison.chunkDeltas()).containsEntry("update-site-import-center.js", 200_000L);
        assertThat(comparison.diagnostics()).anyMatch(value -> value.contains("Chunk"));
    }

    @Test
    void flagsChunkAndResponseRegressionsEvenWhenRenderTimeIsStable() {
        CapabilityPerformanceProperties properties = new CapabilityPerformanceProperties();
        properties.setStorageRoot(temporary.toString());
        CapabilityPerformanceService service = new CapabilityPerformanceService(properties,
                JsonMapper.builder().findAndAddModules().build());
        service.evaluate(new CapabilityPerformanceSample("baseline-io", "capability-center", Instant.now(),
                100, 20_000_000, 2, 100_000, 500, Map.of("readiness", 200L),
                Map.of("capability-center.js", 100_000L)));
        service.evaluate(new CapabilityPerformanceSample("candidate-io", "capability-center", Instant.now(),
                100, 20_000_000, 2, 500_000, 500, Map.of("readiness", 200L),
                Map.of("capability-center.js", 400_000L)));

        CapabilityPerformanceComparison comparison = service.compare("baseline-io", "candidate-io");

        assertThat(comparison.regression()).isTrue();
        assertThat(comparison.responseDelta()).isEqualTo(400_000L);
        assertThat(comparison.chunkDeltas()).containsEntry("capability-center.js", 300_000L);
    }

}
