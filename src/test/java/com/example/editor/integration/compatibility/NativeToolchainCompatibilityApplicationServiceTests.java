package com.example.editor.integration.compatibility;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.example.editor.integration.verification.TechnologyValidationStepResult;
import com.example.editor.integration.verification.TechnologyValidationStepStatus;
import tools.jackson.databind.json.JsonMapper;

class NativeToolchainCompatibilityApplicationServiceTests {
    @TempDir Path root;

    @Test
    void runsASelectedCatalogProfileAndPublishesTheLatestMatrixAtomically(){
        ToolchainCommandRunner runner = (id, description, workingDirectory, command, expectedExitCode, timeoutSeconds, environment) ->
                new TechnologyValidationStepResult(id, description, TechnologyValidationStepStatus.PASSED, command, 0, 4L, "version", "", List.of());
        JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();
        NativeToolchainCompatibilityApplicationService service = new NativeToolchainCompatibilityApplicationService(
                new NativeToolchainProbeCatalog(),
                new NativeToolchainCompatibilityOrchestrator(new NativeToolchainProbeExecutor(runner),
                        new TechnologyCompatibilityMatrixService(Clock.fixed(Instant.parse("2026-07-31T08:00:00Z"), ZoneOffset.UTC))),
                new TechnologyCompatibilityMatrixWriter(mapper), mapper, root);

        TechnologyCompatibilityMatrix matrix = service.run(new NativeToolchainCompatibilityRunRequest(List.of("langium:configured")));

        assertThat(matrix.overallStatus()).isEqualTo(CompatibilityStatus.PASSED);
        assertThat(matrix.results()).extracting(NativeToolchainProbeResult::technology).containsExactly("Langium");
        assertThat(service.latest()).contains(matrix);
        assertThat(root.resolve(TechnologyCompatibilityMatrixWriter.DEFAULT_PATH)).isRegularFile();
    }
}
