package com.example.editor.generated.project;

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.example.editor.integration.verification.TechnologyValidationStepResult;
import com.example.editor.integration.verification.TechnologyValidationStepStatus;
import tools.jackson.databind.json.JsonMapper;

class GeneratedProjectLifecycleServiceTests {
    @TempDir Path temporary;

    @Test
    void registersAndPlansAProjectWithoutExecutingNativeTools() throws Exception{
        Path projects = temporary.resolve("projects");
        Path registry = temporary.resolve("registry");
        Files.createDirectories(projects.resolve("sample"));
        var runner = (com.example.editor.integration.compatibility.ToolchainCommandRunner)
                (id, description, workingDirectory, command, expectedExitCode, timeoutSeconds, environment) ->
                        new TechnologyValidationStepResult(id, description, TechnologyValidationStepStatus.PASSED,
                                command, 0, 1, "", "", List.of());
        var service = new GeneratedProjectLifecycleService(new GeneratedProjectRuntimeProfileCatalog(),
                new GeneratedProjectRegistryStore(JsonMapper.builder().findAndAddModules().build()), runner,
                projects.toString(), registry.toString());
        service.register(new GeneratedProjectDefinition("sample", "Sample", "standalone-web",
                "sample", Instant.EPOCH, Map.of()));

        GeneratedProjectExecutionReport report = service.execute(new GeneratedProjectExecutionRequest(
                "sample", List.of(GeneratedProjectStage.GENERATE, GeneratedProjectStage.VALIDATE,
                GeneratedProjectStage.OPEN), true, false, false));

        assertThat(report.successful()).isTrue();
        assertThat(report.steps()).extracting(GeneratedProjectStepResult::status)
                .containsExactly(GeneratedProjectStepStatus.PLANNED,
                        GeneratedProjectStepStatus.PLANNED, GeneratedProjectStepStatus.PASSED);
        assertThat(report.resolvedDirectory()).endsWith("sample");
    }

    @Test
    void rejectsDirectoriesOutsideTheConfiguredRoot(){
        var service = new GeneratedProjectLifecycleService(new GeneratedProjectRuntimeProfileCatalog(),
                new GeneratedProjectRegistryStore(JsonMapper.builder().build()),
                (id, description, workingDirectory, command, expectedExitCode, timeoutSeconds, environment) -> null,
                temporary.resolve("projects").toString(), temporary.resolve("registry").toString());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.register(
                new GeneratedProjectDefinition("unsafe", "Unsafe", "langium", "../outside",
                        Instant.EPOCH, Map.of()))).isInstanceOf(IllegalArgumentException.class);
    }
}
