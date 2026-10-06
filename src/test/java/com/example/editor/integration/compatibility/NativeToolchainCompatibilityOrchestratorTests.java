package com.example.editor.integration.compatibility;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.example.editor.integration.verification.TechnologyValidationStepResult;
import com.example.editor.integration.verification.TechnologyValidationStepStatus;

class NativeToolchainCompatibilityOrchestratorTests {
    @Test
    void aggregatesRequiredAndOptionalCommandsWithoutHidingPartialSupport(){
        ToolchainCommandRunner runner = (id, description, root, command, expected, timeout, environment) ->
                step(id, id.equals("optional") ? TechnologyValidationStepStatus.TOOL_UNAVAILABLE : TechnologyValidationStepStatus.PASSED);
        NativeToolchainProbeExecutor executor = new NativeToolchainProbeExecutor(runner);
        NativeToolchainProbeResult result = executor.execute(definition("Langium", "4.0", List.of(
                command("required", true), command("optional", false))));
        assertThat(result.status()).isEqualTo(CompatibilityStatus.PARTIAL);
        assertThat(result.evidence()).contains("required=PASSED", "optional=TOOL_UNAVAILABLE");
    }

    @Test
    void failsProbeWhenARequiredCommandCannotRun(){
        ToolchainCommandRunner runner = (id, description, root, command, expected, timeout, environment) ->
                step(id, TechnologyValidationStepStatus.TIMED_OUT);
        assertThat(new NativeToolchainProbeExecutor(runner).execute(definition("GLSP", "2", List.of(command("build", true)))).status())
                .isEqualTo(CompatibilityStatus.FAILED);
    }

    @Test
    void runsDefinitionsAndCommandsInStableOrder(){
        List<String> order = new ArrayList<>();
        ToolchainCommandRunner runner = (id, description, root, command, expected, timeout, environment) -> {
            order.add(command.get(0) + ":" + id); return step(id, TechnologyValidationStepStatus.PASSED);
        };
        NativeToolchainCompatibilityOrchestrator orchestrator = new NativeToolchainCompatibilityOrchestrator(
                new NativeToolchainProbeExecutor(runner), new TechnologyCompatibilityMatrixService(
                        Clock.fixed(Instant.parse("2026-07-31T06:00:00Z"), ZoneOffset.UTC)));
        TechnologyCompatibilityMatrix matrix = orchestrator.execute(List.of(
                definition("Xtext", "2.39", List.of(command("z", true), command("a", true))),
                definition("EMF", "2026", List.of(command("check", true)))));
        assertThat(matrix.results()).extracting(NativeToolchainProbeResult::technology).containsExactly("EMF", "Xtext");
        assertThat(order).containsExactly("check:check", "a:a", "z:z");
    }

    @Test
    void rejectsDuplicateDefinitionKeys(){
        NativeToolchainCompatibilityOrchestrator orchestrator = new NativeToolchainCompatibilityOrchestrator(
                new NativeToolchainProbeExecutor((id, description, root, command, expected, timeout, environment) -> step(id, TechnologyValidationStepStatus.PASSED)),
                new TechnologyCompatibilityMatrixService());
        assertThatThrownBy(() -> orchestrator.execute(List.of(
                definition("Xtext", "2", List.of()), definition("xtext", "2", List.of()))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Duplicate");
    }

    @Test
    void rejectsDuplicateCommandIdentifiersInsideOneDefinition(){
        assertThatThrownBy(() -> definition("EMF", "2026", List.of(command("build", true), command("build", false))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Duplicate probe command");
    }

    private NativeToolchainProbeDefinition definition(String technology, String version, List<NativeToolchainProbeCommand> commands){
        return new NativeToolchainProbeDefinition(technology, version, Path.of("."), commands, Map.of());
    }
    private NativeToolchainProbeCommand command(String id, boolean required){
        return new NativeToolchainProbeCommand(id, "Run " + id, List.of(id), 0, 10, required);
    }
    private TechnologyValidationStepResult step(String id, TechnologyValidationStepStatus status){
        return new TechnologyValidationStepResult(id, id, status, List.of(id), status == TechnologyValidationStepStatus.PASSED ? 0 : null,
                5L, "version 1", "", List.of());
    }
}
