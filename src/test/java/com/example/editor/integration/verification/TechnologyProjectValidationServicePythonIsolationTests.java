package com.example.editor.integration.verification;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.editor.integration.IntegrationTechnology;
import com.example.editor.integration.IntegrationVerificationStep;
import com.example.editor.integration.compiler.TechnologyCompilationResult;

/**
 * The native validation of a generated Python package (BESSER SDK: «python -m pip install -e .[test]» and «python -m pytest») used to run
 * with whatever python the server found on its PATH, so validating a package installed it, and its dependencies, into the interpreter of
 * the host. The declared steps are kept; they now run with the interpreter of an environment created for the validation.
 */
class TechnologyProjectValidationServicePythonIsolationTests {
    private static final boolean WINDOWS = System.getProperty("os.name", "").toLowerCase().contains("win");

    @Test
    void pythonStepsRunWithTheInterpreterOfAnEnvironmentCreatedForTheValidationAndNeverWithTheHostOne(@TempDir Path temporary) {
        var runner = new RecordingRunner(true);
        var report = validate(temporary, runner, List.of(pip(), pytest(), maven()));

        List<String> creation = runner.commands.get(0);
        assertThat(creation.subList(1, 3)).containsExactly("-m", "venv");
        assertThat(executable(creation)).isIn("python", "python.exe");
        Path interpreter = interpreterOf(Path.of(creation.get(3)));
        assertThat(runner.commands.get(1)).containsExactly(interpreter.toString(), "-m", "pip", "install", "-e", ".[test]");
        assertThat(runner.commands.get(2)).containsExactly(interpreter.toString(), "-m", "pytest", "-q");
        assertThat(executable(runner.commands.get(3))).isIn("mvn", "mvn.cmd");
        // a bare «python» would be resolved through the PATH of the server, that is, the interpreter of the host
        assertThat(runner.commands.subList(1, runner.commands.size())).noneMatch(command -> command.get(0).matches("(?i)python3?(\\.exe)?"));
        assertThat(report.steps()).extracting(TechnologyValidationStepResult::id).contains("python-isolated-environment", "install", "test", "build");
        assertThat(report.metadata()).containsKey("pythonEnvironment");
        assertThat(Path.of(report.metadata().get("pythonEnvironment"))).isEqualTo(Path.of(creation.get(3)).getParent());
    }

    @Test
    void withoutAnIsolatedEnvironmentThePythonStepsAreNotRunAndTheValidationDoesNotPass(@TempDir Path temporary) {
        var runner = new RecordingRunner(false);
        var report = validate(temporary, runner, List.of(pip(), pytest()));

        assertThat(runner.commands).hasSize(1);
        assertThat(report.steps()).filteredOn(step -> step.id().equals("install") || step.id().equals("test"))
                .hasSize(2).allSatisfy(step -> {
                    assertThat(step.status()).isEqualTo(TechnologyValidationStepStatus.SKIPPED);
                    assertThat(step.diagnostics()).anyMatch(text -> text.contains("does not install generated packages into the host interpreter"));
                });
        assertThat(report.status()).isEqualTo(TechnologyValidationStatus.FAILED);
    }

    @Test
    void stepsWithoutPythonDoNotCreateAnEnvironment(@TempDir Path temporary) {
        var runner = new RecordingRunner(true);
        var report = validate(temporary, runner, List.of(maven()));

        assertThat(runner.commands).hasSize(1);
        assertThat(executable(runner.commands.get(0))).isIn("mvn", "mvn.cmd");
        assertThat(report.metadata()).doesNotContainKey("pythonEnvironment");
    }

    private static IntegrationVerificationStep pip() {return new IntegrationVerificationStep("install", "Install", ".", List.of("python", "-m", "pip", "install", "-e", ".[test]"), 0, true);}
    private static IntegrationVerificationStep pytest() {return new IntegrationVerificationStep("test", "Test", ".", List.of("python", "-m", "pytest", "-q"), 0, true);}
    private static IntegrationVerificationStep maven() {return new IntegrationVerificationStep("build", "Build", ".", List.of("mvn", "-q", "verify"), 0, true);}

    private static String executable(List<String> command) {return Path.of(command.get(0)).getFileName().toString().toLowerCase();}
    private static Path interpreterOf(Path environment) {return WINDOWS ? environment.resolve("Scripts").resolve("python.exe") : environment.resolve("bin").resolve("python");}

    private TechnologyProjectValidationReport validate(Path temporary, ExternalToolProcessRunner runner, List<IntegrationVerificationStep> steps) {
        var compilation = new TechnologyCompilationResult(IntegrationTechnology.XTEXT, List.of(), List.of(), List.of(), List.of(), steps, List.of(), Map.of());
        var validators = new TechnologyProjectStructureValidatorRegistry(List.of(new XtextProjectStructureValidator()));
        var properties = new TechnologyProjectValidationProperties();
        properties.setWorkspaceRoot(temporary);
        properties.setDefaultTimeoutSeconds(30);
        var service = new TechnologyProjectValidationService(validators, runner, properties);
        return service.validate(compilation, new TechnologyProjectValidationRequest(TechnologyValidationMode.NATIVE_REQUIRED, null, 30, false, Map.of()));
    }

    /** Records every command; «python -m venv <dir>» creates the interpreter file when the environment is meant to succeed. */
    private static final class RecordingRunner extends ExternalToolProcessRunner {
        private final boolean environmentSucceeds;
        private final List<List<String>> commands = new ArrayList<>();

        RecordingRunner(boolean environmentSucceeds) {super(new ExternalToolCircuitBreaker()); this.environmentSucceeds = environmentSucceeds;}

        @Override public TechnologyValidationStepResult run(String id, String description, Path workingDirectory, List<String> command,
                int expectedExitCode, int timeoutSeconds, Map<String, String> environment) {
            commands.add(List.copyOf(command));
            boolean venv = command.size() > 3 && command.get(1).equals("-m") && command.get(2).equals("venv");
            if(venv && environmentSucceeds) {
                try {Path interpreter = interpreterOf(Path.of(command.get(3))); Files.createDirectories(interpreter.getParent()); Files.writeString(interpreter, "");}
                catch(IOException exception) {throw new UncheckedIOException(exception);}
            }
            var status = venv && !environmentSucceeds ? TechnologyValidationStepStatus.FAILED : TechnologyValidationStepStatus.PASSED;
            return new TechnologyValidationStepResult(id, description, status, command, status == TechnologyValidationStepStatus.PASSED ? 0 : 1, 1, "", "", List.of());
        }
    }
}
