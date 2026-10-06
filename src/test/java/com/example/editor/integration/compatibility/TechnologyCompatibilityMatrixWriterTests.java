package com.example.editor.integration.compatibility;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import tools.jackson.databind.json.JsonMapper;
import com.example.editor.product.HydraReleaseVersion;

class TechnologyCompatibilityMatrixWriterTests {
    @TempDir Path root;
    @Test
    void writesAnAtomicReadableReportBelowTheSelectedRoot() throws Exception{
        TechnologyCompatibilityMatrix matrix = new TechnologyCompatibilityMatrix(HydraReleaseVersion.VERSION, Instant.EPOCH,
                CompatibilityStatus.PASSED, List.of(new NativeToolchainProbeResult("EMF", "2026", CompatibilityStatus.PASSED,
                        "ok", List.of("mvn=PASSED"), 10)));
        Path target = new TechnologyCompatibilityMatrixWriter(JsonMapper.builder().findAndAddModules().build()).write(root, matrix);
        assertThat(target).exists();
        assertThat(java.nio.file.Files.readString(target)).contains("\"hydraVersion\"", "\"" + HydraReleaseVersion.VERSION + "\"", "mvn=PASSED");
    }
    @Test
    void rejectsPathsThatEscapeTheReportRoot(){
        TechnologyCompatibilityMatrix matrix = new TechnologyCompatibilityMatrix(HydraReleaseVersion.VERSION, Instant.EPOCH, CompatibilityStatus.NOT_RUN, List.of());
        assertThatThrownBy(() -> new TechnologyCompatibilityMatrixWriter(JsonMapper.builder().build()).write(root, "../matrix.json", matrix))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Unsafe");
    }
}
