package com.example.editor.grammar.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
import com.example.editor.grammar.runtime.GrammarNativeValidationResult;
import com.example.editor.integration.verification.ExternalToolCircuitBreaker;
import com.example.editor.integration.verification.ExternalToolProcessRunner;
import com.example.editor.integration.verification.TechnologyValidationStepResult;
import com.example.editor.integration.verification.TechnologyValidationStepStatus;
import com.example.editor.security.process.HydraTemporaryWorkspace;

/**
 * BLQ-GR-NATIVE (autorización del 30/10, punto 3): la preparación de dependencias tiene su propio presupuesto, build y tests conservan el del
 * perfil, una segunda verificación del mismo package-lock.json no vuelve a instalar las dependencias, un plazo vencido informa de la carga del
 * sistema y la verificación sigue distinguiendo PASS, FAIL y BLOCKED (diferida).
 */
class GrammarNativeDependencyReuseTests {
    private static final String LOCK = "{\"lockfileVersion\":3,\"packages\":{\"node_modules/langium\":{\"version\":\"4.3.0\"}}}";
    @TempDir Path directory;

    @Test void theSecondVerificationOfTheSameLockfileRestoresTheDependenciesWithoutNpm() {
        RecordingRunner runner = new RecordingRunner(null);
        GrammarNativeToolchainService service = service(runner);

        GrammarNativeValidationResult first = service.validate(grammar(), profile(), project(LOCK), runtime());
        GrammarNativeValidationResult second = service.validate(grammar(), profile(), project(LOCK), runtime());

        assertThat(first.successful()).isTrue();
        assertThat(first.metadata()).containsEntry("dependencySource", "NPM").containsEntry("dependencyTemplateStored", "true");
        assertThat(second.successful()).isTrue();
        assertThat(second.metadata()).containsEntry("dependencySource", "TEMPLATE");
        assertThat(runner.ids).containsExactly("langium-dependencies", "langium-build", "langium-tests", "langium-build", "langium-tests");
        assertThat(second.steps().getFirst().id()).isEqualTo("langium-dependencies");
        assertThat(second.steps().getFirst().status()).isEqualTo("PASSED");
        assertThat(second.steps().getFirst().diagnostics()).anyMatch(text -> text.contains("npm was not run"));
        assertThat(runner.nodeModulesSeenByBuild).allSatisfy(content -> assertThat(content).isEqualTo("export const langium = 4;"));
    }

    @Test void aDifferentLockfileInstallsItsOwnDependencies() {
        RecordingRunner runner = new RecordingRunner(null);
        GrammarNativeToolchainService service = service(runner);

        service.validate(grammar(), profile(), project(LOCK), runtime());
        GrammarNativeValidationResult other = service.validate(grammar(), profile(), project(LOCK.replace("4.3.0", "4.3.1")), runtime());

        assertThat(other.metadata()).containsEntry("dependencySource", "NPM");
        assertThat(runner.ids.stream().filter("langium-dependencies"::equals)).hasSize(2);
    }

    @Test void withoutALockfileNothingIsReusedAndVersionsAreResolvedByNpmInstall() {
        RecordingRunner runner = new RecordingRunner(null);
        GrammarNativeToolchainService service = service(runner);

        service.validate(grammar(), profile(), project(null), runtime());
        GrammarNativeValidationResult second = service.validate(grammar(), profile(), project(null), runtime());

        assertThat(second.metadata()).containsEntry("dependencySource", "NPM").containsEntry("dependencyTemplateKey", "");
        assertThat(runner.commands.getFirst()).contains("install").doesNotContain("--prefer-offline");
        assertThat(directory.resolve("templates")).doesNotExist();
    }

    @Test void theDependencyStepHasItsOwnBudgetAndBuildAndTestsKeepTheProfileBudget() {
        RecordingRunner runner = new RecordingRunner(null);
        service(runner).validate(grammar(), profile(), project(LOCK), runtime());

        assertThat(runner.timeouts).containsEntry("langium-dependencies", 180).containsEntry("langium-build", 60).containsEntry("langium-tests", 60);
        assertThat(runner.commands.getFirst()).contains("ci", "--ignore-scripts", "--prefer-offline");
    }

    @Test void anExpiredDependencyBudgetIsDeferredWithTheBudgetAndTheSystemLoad() {
        RecordingRunner runner = new RecordingRunner(TechnologyValidationStepStatus.TIMED_OUT);
        GrammarNativeValidationResult result = service(runner).validate(grammar(), profile(), project(LOCK), runtime());

        assertThat(result.successful()).isFalse();
        assertThat(result.verificationDeferred()).isTrue();
        assertThat(result.definitiveFailure()).isFalse();
        assertThat(result.steps().getFirst().diagnostics()).anyMatch(text -> text.contains("Step budget 180 s exceeded") && text.contains("system CPU load"));
        assertThat(directory.resolve("templates")).doesNotExist();
    }

    /** A failed dependency acquisition is an environment limitation by contract (GrammarNativeValidationResult): deferred, never stored. */
    @Test void aFailedInstallationIsDeferredAndIsNeverStored() {
        RecordingRunner runner = new RecordingRunner(TechnologyValidationStepStatus.FAILED);
        GrammarNativeValidationResult result = service(runner).validate(grammar(), profile(), project(LOCK), runtime());

        assertThat(result.successful()).isFalse();
        assertThat(result.verificationDeferred()).isTrue();
        assertThat(directory.resolve("templates")).doesNotExist();
    }

    private GrammarNativeToolchainService service(RecordingRunner runner) {
        GrammarProperties properties = new GrammarProperties();
        properties.setNativeCaptureMaximumFiles(8);
        properties.setNativeCaptureMaximumFileBytes(16 * 1024);
        properties.setNativeCaptureMaximumBytes(32 * 1024);
        properties.setNativeDependencyTemplateDirectory(directory.resolve("templates"));
        return new GrammarNativeToolchainService(runner, new Workspaces(directory), properties);
    }

    private GrammarDefinitionModel grammar() {
        return new GrammarDefinitionModel("1.0", "people", "People", GrammarTechnology.LANGIUM, GrammarAuthoringMode.GRAMMAR_FIRST, "people-mm",
                "langium-4.3-browser", "main.langium", "Model", List.of(), GrammarSemanticBindingMap.empty(), "", Map.of(), Map.of());
    }

    private GrammarToolchainProfile profile() {
        return new GrammarToolchainProfile("langium-4.3-browser", GrammarTechnology.LANGIUM, "4.3.0", "4.3.0", "", "22", "npm", "TypeScript", false,
                1000, 60_000, 128 * 1024 * 1024L, GrammarExecutionMode.INTERNAL, Map.of("browserWorker", "true"));
    }

    private GeneratedGrammarProject project(String lock) {
        Map<String, String> files = new LinkedHashMap<>(Map.of("package.json", "{}", "main.langium", "grammar People"));
        if(lock != null) files.put("package-lock.json", lock);
        return new GeneratedGrammarProject("people-langium", files, Map.of());
    }

    private GrammarRuntimeDescriptor runtime() {
        return new GrammarRuntimeDescriptor("LANGIUM_BROWSER_WORKER_OR_LSP", "Model", List.of(".people"), "node ./dist/language/main.js --stdio",
                "./dist-browser/hydra-language-worker.js", Map.of());
    }

    /** Simula npm: el paso de dependencias deja un node_modules y el build comprueba que existe y lo lee. */
    private static final class RecordingRunner extends ExternalToolProcessRunner {
        private final TechnologyValidationStepStatus dependencyStatus;
        private final List<String> ids = new ArrayList<>();
        private final List<List<String>> commands = new ArrayList<>();
        private final Map<String, Integer> timeouts = new LinkedHashMap<>();
        private final List<String> nodeModulesSeenByBuild = new ArrayList<>();

        private RecordingRunner(TechnologyValidationStepStatus dependencyStatus) { super((ExternalToolCircuitBreaker)null); this.dependencyStatus = dependencyStatus; }

        @Override public TechnologyValidationStepResult run(String id, String description, Path workingDirectory, List<String> command, int expectedExitCode,
                int timeoutSeconds, Map<String, String> environment) {
            ids.add(id);
            commands.add(List.copyOf(command));
            timeouts.put(id, timeoutSeconds);
            try {
                if(id.endsWith("dependencies")) {
                    if(dependencyStatus != null) return result(id, description, command, dependencyStatus, "npm did not finish");
                    Path module = workingDirectory.resolve("node_modules/langium/lib/index.js");
                    Files.createDirectories(module.getParent());
                    Files.writeString(module, "export const langium = 4;", StandardCharsets.UTF_8);
                }
                if(id.equals("langium-build")) {
                    nodeModulesSeenByBuild.add(Files.readString(workingDirectory.resolve("node_modules/langium/lib/index.js")));
                    Path worker = workingDirectory.resolve("dist-browser/hydra-language-worker.js");
                    Files.createDirectories(worker.getParent());
                    Files.writeString(worker, "self.onmessage=()=>{};", StandardCharsets.UTF_8);
                }
                return result(id, description, command, TechnologyValidationStepStatus.PASSED, "");
            } catch(Exception exception) {
                return result(id, description, command, TechnologyValidationStepStatus.FAILED, String.valueOf(exception));
            }
        }

        private TechnologyValidationStepResult result(String id, String description, List<String> command, TechnologyValidationStepStatus status, String stderr) {
            return new TechnologyValidationStepResult(id, description, status, command, status == TechnologyValidationStepStatus.PASSED ? 0 : 1, 1L, "", stderr, List.of());
        }
    }

    private static final class Workspaces extends HydraTemporaryWorkspace {
        private final Path root;
        private Workspaces(Path root) { super(null); this.root = root; }
        @Override public Path create(String prefix) {
            try { return Files.createTempDirectory(root, prefix); } catch(Exception exception) { throw new IllegalStateException(exception); }
        }
        @Override public void cleanup(Path workspace) {
            if(workspace == null || !Files.exists(workspace)) return;
            try(var paths = Files.walk(workspace)) {
                paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> { try { Files.deleteIfExists(path); } catch(Exception ignored) { } });
            } catch(Exception ignored) { }
        }
    }
}
