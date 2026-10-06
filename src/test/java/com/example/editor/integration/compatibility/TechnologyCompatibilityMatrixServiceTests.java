package com.example.editor.integration.compatibility;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class TechnologyCompatibilityMatrixServiceTests {
    private final TechnologyCompatibilityMatrixService service = new TechnologyCompatibilityMatrixService(
            Clock.fixed(Instant.parse("2026-07-30T18:00:00Z"), ZoneOffset.UTC));

    @Test
    void sortsTechnologyAndVersionDeterministically(){
        TechnologyCompatibilityMatrix result = service.build(List.of(
                probe("Xtext", "2.39", CompatibilityStatus.PASSED),
                probe("BESSER", "5", CompatibilityStatus.PASSED),
                probe("Xtext", "2.38", CompatibilityStatus.PASSED)));
        assertThat(result.results()).extracting(NativeToolchainProbeResult::key)
                .containsExactly("besser:5", "xtext:2.38", "xtext:2.39");
    }

    @Test
    void reportsFailedWhenAnyProbeFails(){
        TechnologyCompatibilityMatrix result = service.build(List.of(
                probe("Langium", "4", CompatibilityStatus.PASSED),
                probe("GLSP", "2", CompatibilityStatus.FAILED)));
        assertThat(result.overallStatus()).isEqualTo(CompatibilityStatus.FAILED);
    }

    @Test
    void reportsPartialWhenAProbeIsPartialOrNotRun(){
        assertThat(service.build(List.of(probe("EMF", "2026-03", CompatibilityStatus.PARTIAL))).overallStatus())
                .isEqualTo(CompatibilityStatus.PARTIAL);
        assertThat(service.build(List.of(
                probe("EMF", "2026-03", CompatibilityStatus.PASSED),
                probe("Sirius", "7", CompatibilityStatus.NOT_RUN))).overallStatus())
                .isEqualTo(CompatibilityStatus.PARTIAL);
    }

    @Test
    void rejectsDuplicateTechnologyVersionRows(){
        assertThatThrownBy(() -> service.build(List.of(
                probe("Xtext", "2.39", CompatibilityStatus.PASSED),
                probe("xtext", "2.39", CompatibilityStatus.PARTIAL))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate");
    }

    private NativeToolchainProbeResult probe(String technology, String version, CompatibilityStatus status){
        return new NativeToolchainProbeResult(technology, version, status, "checked", List.of("log"), 10);
    }
}
