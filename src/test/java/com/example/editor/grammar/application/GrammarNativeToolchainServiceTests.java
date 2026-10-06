package com.example.editor.grammar.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.editor.grammar.GrammarProperties;
import com.example.editor.grammar.core.GeneratedGrammarProject;
import com.example.editor.grammar.core.GrammarAuthoringMode;
import com.example.editor.grammar.core.GrammarDefinitionModel;
import com.example.editor.grammar.core.GrammarExecutionMode;
import com.example.editor.grammar.core.GrammarRuntimeDescriptor;
import com.example.editor.grammar.core.GrammarSemanticBindingMap;
import com.example.editor.grammar.core.GrammarTechnology;
import com.example.editor.grammar.core.GrammarToolchainProfile;
import com.example.editor.integration.verification.ExternalToolCircuitBreaker;
import com.example.editor.integration.verification.ExternalToolProcessRunner;
import com.example.editor.integration.verification.TechnologyValidationStepResult;
import com.example.editor.integration.verification.TechnologyValidationStepStatus;
import com.example.editor.security.process.HydraTemporaryWorkspace;

class GrammarNativeToolchainServiceTests {
    @TempDir Path directory;

    @Test void capturesMaterializedWorkerAndResolvedPackageLockBeforeWorkspaceCleanup() {
        GrammarProperties properties = properties();
        RecordingWorkspace workspace = new RecordingWorkspace(directory);
        RecordingRunner runner = new RecordingRunner(Mode.OK);
        var result = new GrammarNativeToolchainService(runner, workspace, properties)
                .validate(grammar(), profile(), project(), runtime());

        assertThat(result.successful()).isTrue();
        assertThat(result.runtimeFiles()).containsEntry("dist-browser/hydra-language-worker.js",
                "self.onmessage=()=>{};");
        assertThat(result.reproducibilityFiles()).containsKey("package-lock.json");
        assertThat(result.steps()).anySatisfy(step -> {
            assertThat(step.id()).isEqualTo("native-artifact-capture");
            assertThat(step.status()).isEqualTo("PASSED");
        });
        assertThat(workspace.cleaned).hasSize(1);
        assertThat(workspace.cleaned.getFirst()).doesNotExist();
        assertThat(runner.environments).allSatisfy(environment -> {
            assertThat(environment).containsOnlyKeys("MAVEN_OPTS", "NODE_OPTIONS");
            assertThat(environment.get("MAVEN_OPTS")).contains("-Xmx512m", "-Dfile.encoding=UTF-8");
            assertThat(environment.get("NODE_OPTIONS")).isEqualTo("--max-old-space-size=512");
        });
    }

    @Test void failsInsteadOfClaimingBundledRuntimeWhenDeclaredWorkerIsMissing() {
        var result = new GrammarNativeToolchainService(new RecordingRunner(Mode.MISSING_WORKER),
                new RecordingWorkspace(directory), properties()).validate(grammar(), profile(), project(), runtime());
        assertThat(result.successful()).isFalse();
        assertThat(result.runtimeFiles()).isEmpty();
        assertThat(result.steps()).anySatisfy(step -> {
            assertThat(step.id()).isEqualTo("native-artifact-capture");
            assertThat(step.status()).isEqualTo("FAILED");
            assertThat(step.diagnostics()).anyMatch(message -> message.contains("was not materialized"));
        });
    }

    @Test void neverCapturesPartialRuntimeAfterNativeBuildFailure() {
        var result = new GrammarNativeToolchainService(new RecordingRunner(Mode.FAIL_BUILD),
                new RecordingWorkspace(directory), properties()).validate(grammar(), profile(), project(), runtime());
        assertThat(result.successful()).isFalse();
        assertThat(result.runtimeFiles()).isEmpty();
        assertThat(result.reproducibilityFiles()).isEmpty();
        assertThat(result.steps()).noneMatch(step -> step.id().equals("native-artifact-capture"));
    }

    @Test void rejectsRuntimeFilesOverTheIndependentCaptureBudget() {
        GrammarProperties properties = properties();
        properties.setNativeCaptureMaximumFileBytes(1024);
        properties.setNativeCaptureMaximumBytes(2048);
        var result = new GrammarNativeToolchainService(new RecordingRunner(Mode.HUGE_WORKER),
                new RecordingWorkspace(directory), properties).validate(grammar(), profile(), project(), runtime());
        assertThat(result.successful()).isFalse();
        assertThat(result.runtimeFiles()).isEmpty();
        assertThat(result.steps()).anyMatch(step -> step.id().equals("native-artifact-capture")
                && step.status().equals("FAILED"));
    }

    @Test void usesNpmCiWhenAResolvedLockIsAlreadyPresent() {
        RecordingRunner runner = new RecordingRunner(Mode.OK);
        GeneratedGrammarProject locked = new GeneratedGrammarProject("people-langium", Map.of(
                "package.json", "{}", "package-lock.json", "{\"lockfileVersion\":3}",
                "main.langium", "grammar People"), Map.of());
        var result = new GrammarNativeToolchainService(runner, new RecordingWorkspace(directory), properties())
                .validate(grammar(), profile(), locked, runtime());
        assertThat(result.successful()).isTrue();
        List<String> command = runner.commands.getFirst();
        String expectedNpm = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win")
                ? "npm.cmd" : "npm";
        assertThat(command).hasSizeGreaterThanOrEqualTo(2);
        assertThat(command.getFirst()).isEqualTo(expectedNpm);
        assertThat(command.get(1)).isEqualTo("ci");
    }

    private GrammarProperties properties() {
        GrammarProperties value = new GrammarProperties();
        value.setNativeCaptureMaximumFiles(8);
        value.setNativeCaptureMaximumFileBytes(16 * 1024);
        value.setNativeCaptureMaximumBytes(32 * 1024);
        return value;
    }

    private GrammarDefinitionModel grammar() {
        return new GrammarDefinitionModel("1.0", "people", "People", GrammarTechnology.LANGIUM,
                GrammarAuthoringMode.GRAMMAR_FIRST, "people-mm", "langium-4.3-browser", "main.langium", "Model",
                List.of(), GrammarSemanticBindingMap.empty(), "", Map.of(), Map.of());
    }

    private GrammarToolchainProfile profile() {
        return new GrammarToolchainProfile("langium-4.3-browser", GrammarTechnology.LANGIUM, "4.3.0", "4.3.0",
                "", "22", "npm", "TypeScript", false, 1000, 5000, 128 * 1024 * 1024L,
                GrammarExecutionMode.INTERNAL, Map.of("browserWorker", "true"));
    }

    private GeneratedGrammarProject project() {
        return new GeneratedGrammarProject("people-langium", Map.of("package.json", "{}",
                "main.langium", "grammar People"), Map.of());
    }

    private GrammarRuntimeDescriptor runtime() {
        return new GrammarRuntimeDescriptor("LANGIUM_BROWSER_WORKER_OR_LSP", "Model", List.of(".people"),
                "node ./dist/language/main.js --stdio", "./dist-browser/hydra-language-worker.js", Map.of());
    }

    private enum Mode { OK, MISSING_WORKER, HUGE_WORKER, FAIL_BUILD }

    private static final class RecordingRunner extends ExternalToolProcessRunner {
        private final Mode mode;
        private final List<List<String>> commands = new ArrayList<>();
        private final List<Map<String,String>> environments = new ArrayList<>();

        private RecordingRunner(Mode mode) { super((ExternalToolCircuitBreaker)null); this.mode = mode; }

        @Override public TechnologyValidationStepResult run(String id, String description, Path workingDirectory,
                List<String> command, int expectedExitCode, int timeoutSeconds, Map<String,String> environment) {
            commands.add(List.copyOf(command));
            environments.add(Map.copyOf(environment));
            try {
                if(id.endsWith("dependencies")) Files.writeString(workingDirectory.resolve("package-lock.json"),
                        "{\"lockfileVersion\":3}", StandardCharsets.UTF_8);
                if(id.equals("langium-build")) {
                    if(mode == Mode.FAIL_BUILD) return result(id, description, command, TechnologyValidationStepStatus.FAILED, "build failed");
                    Path worker = workingDirectory.resolve("dist-browser/hydra-language-worker.js");
                    Files.createDirectories(worker.getParent());
                    if(mode == Mode.HUGE_WORKER) Files.writeString(worker, "x".repeat(8192), StandardCharsets.UTF_8);
                    else if(mode != Mode.MISSING_WORKER) Files.writeString(worker, "self.onmessage=()=>{};", StandardCharsets.UTF_8);
                }
                return result(id, description, command, TechnologyValidationStepStatus.PASSED, "");
            } catch(Exception exception) {
                return result(id, description, command, TechnologyValidationStepStatus.FAILED, exception.getMessage());
            }
        }

        private TechnologyValidationStepResult result(String id, String description, List<String> command,
                TechnologyValidationStepStatus status, String stderr) {
            return new TechnologyValidationStepResult(id, description, status, command,
                    status == TechnologyValidationStepStatus.PASSED ? 0 : 1, 1L, "", stderr, List.of());
        }
    }

    private static final class RecordingWorkspace extends HydraTemporaryWorkspace {
        private final Path root;
        private final List<Path> cleaned = new ArrayList<>();
        private RecordingWorkspace(Path root) { super(null); this.root = root; }
        @Override public Path create(String prefix) {
            try { return Files.createTempDirectory(root, prefix); }
            catch(Exception exception) { throw new IllegalStateException(exception); }
        }
        @Override public void cleanup(Path workspace) {
            cleaned.add(workspace);
            if(workspace == null || !Files.exists(workspace)) return;
            try(var paths = Files.walk(workspace)) {
                paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                    try { Files.deleteIfExists(path); } catch(Exception ignored) { }
                });
            } catch(Exception ignored) { }
        }
    }
}
