package com.example.editor.grammar.application;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.example.editor.grammar.GrammarProperties;
import com.example.editor.grammar.core.GeneratedGrammarProject;
import com.example.editor.grammar.core.GrammarDefinitionModel;
import com.example.editor.grammar.core.GrammarPathPolicy;
import com.example.editor.grammar.core.GrammarRuntimeDescriptor;
import com.example.editor.grammar.core.GrammarTechnology;
import com.example.editor.grammar.core.GrammarToolchainProfile;
import com.example.editor.grammar.runtime.GrammarNativeValidationResult;
import com.example.editor.grammar.runtime.GrammarNativeValidationStep;
import com.example.editor.integration.verification.ExternalToolProcessRunner;
import com.example.editor.integration.verification.TechnologyValidationStepResult;
import com.example.editor.security.process.HydraTemporaryWorkspace;

/**
 * Server-owned native validation. Imported grammar text never supplies executable commands or environment variables.
 * The temporary workspace is destroyed after bounded, explicitly-declared runtime/build evidence has been captured.
 */
@Service
public class GrammarNativeToolchainService {
    private static final String PACKAGE_LOCK = "package-lock.json";
    private final ExternalToolProcessRunner runner;
    private final HydraTemporaryWorkspace workspaces;
    private final GrammarProperties properties;

    /** Descripción: Inicializa una instancia de GrammarNativeToolchainService con sus dependencias y datos de entrada. Entradas: runner: dato de entrada necesario para la operación; workspaces: dato de entrada necesario para la operación; properties: configuración que gobierna el comportamiento. Salida: deja la instancia inicializada y lista para su uso. */ public GrammarNativeToolchainService(ExternalToolProcessRunner runner, HydraTemporaryWorkspace workspaces,
            GrammarProperties properties) {
        this.runner = runner;
        this.workspaces = workspaces;
        this.properties = properties;
    }

    /** Compatibility entry point for callers that do not need native runtime capture. */
    /** Descripción: Valida las condiciones necesarias para «validate» y rechaza entradas no válidas. Entradas: grammar: dato de entrada necesario para la operación; profile: archivo o contenido que debe procesarse; project: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public GrammarNativeValidationResult validate(GrammarDefinitionModel grammar, GrammarToolchainProfile profile,
            GeneratedGrammarProject project) {
        return validate(grammar, profile, project, new GrammarRuntimeDescriptor("", "", List.of(), "", "", Map.of()));
    }

    /** Descripción: Valida las condiciones necesarias para «validate» y rechaza entradas no válidas. Entradas: grammar: dato de entrada necesario para la operación; profile: archivo o contenido que debe procesarse; project: dato de entrada necesario para la operación; runtimeDescriptor: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public GrammarNativeValidationResult validate(GrammarDefinitionModel grammar, GrammarToolchainProfile profile,
            GeneratedGrammarProject project, GrammarRuntimeDescriptor runtimeDescriptor) {
        Path workspace = workspaces.create("grammar-" + grammar.technology().name().toLowerCase(Locale.ROOT) + "-");
        List<GrammarNativeValidationStep> steps = new ArrayList<>();
        Map<String, String> runtimeFiles = Map.of();
        Map<String, String> reproducibilityFiles = Map.of();
        try {
            materialize(workspace, project);
            Map<String, String> environment = nativeEnvironment(properties.getNativeMaximumHeapMegabytes());
            for(CommandSpec spec : commands(grammar, profile, project)) {
                TechnologyValidationStepResult result = runner.run(spec.id(), spec.description(), workspace, spec.command(), 0,
                        Math.max(1, (int)Math.ceil(profile.compileTimeoutMs() / 1000d)), environment);
                steps.add(new GrammarNativeValidationStep(result.id(), result.status().name(), result.durationMillis(),
                        result.exitCode(), bounded(result.diagnostics(), result.stderr(), properties.getNativeDiagnosticLimit(),
                                properties.getNativeDiagnosticMessageMaxCharacters())));
                if(!result.successful()) break;
            }
            boolean commandsPassed = !steps.isEmpty() && steps.stream().allMatch(step -> "PASSED".equals(step.status()));
            if(commandsPassed) {
                long started = System.nanoTime();
                try {
                    CaptureResult capture = captureNativeEvidence(workspace, runtimeDescriptor);
                    runtimeFiles = capture.runtimeFiles();
                    reproducibilityFiles = capture.reproducibilityFiles();
                    if(capture.requestedFiles() > 0 || !capture.reproducibilityFiles().isEmpty()) {
                        steps.add(new GrammarNativeValidationStep("native-artifact-capture", "PASSED",
                                elapsedMillis(started), 0, List.of("Captured " + capture.requestedFiles()
                                        + " runtime file(s) and " + capture.reproducibilityFiles().size()
                                        + " reproducibility file(s) before workspace cleanup.")));
                    }
                } catch(RuntimeException exception) {
                    steps.add(new GrammarNativeValidationStep("native-artifact-capture", "FAILED",
                            elapsedMillis(started), null, List.of(cut(exception.getMessage(),
                                    properties.getNativeDiagnosticMessageMaxCharacters()))));
                }
            }
            boolean success = !steps.isEmpty() && steps.stream().allMatch(step -> "PASSED".equals(step.status()));
            long capturedBytes = byteSize(runtimeFiles) + byteSize(reproducibilityFiles);
            Map<String, String> metadata = new LinkedHashMap<>();
            metadata.put("workspacePolicy", "TEMPORARY_ISOLATED");
            metadata.put("commandSource", "SERVER_PROFILE");
            metadata.put("shell", "false");
            metadata.put("toolchainProfile", profile.id());
            metadata.put("maximumHeapMegabytes", Integer.toString(properties.getNativeMaximumHeapMegabytes()));
            metadata.put("runtimeFilesCaptured", Integer.toString(runtimeFiles.size()));
            metadata.put("reproducibilityFilesCaptured", Integer.toString(reproducibilityFiles.size()));
            metadata.put("capturedBytes", Long.toString(capturedBytes));
            return new GrammarNativeValidationResult(true, success, steps, Map.copyOf(metadata), runtimeFiles,
                    reproducibilityFiles);
        } finally {
            workspaces.cleanup(workspace);
        }
    }

    /** Descripción: Ejecuta la operación «commands» correspondiente a la responsabilidad de CaptureBudget. Entradas: grammar: dato de entrada necesario para la operación; profile: archivo o contenido que debe procesarse; project: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<CommandSpec> commands(GrammarDefinitionModel grammar, GrammarToolchainProfile profile,
            GeneratedGrammarProject project) {
        List<String> npmDependencies = project.files().containsKey(PACKAGE_LOCK)
                ? List.of(npm(), "ci", "--ignore-scripts", "--no-audit", "--no-fund")
                : List.of(npm(), "install", "--ignore-scripts", "--no-audit", "--no-fund");
        return switch(grammar.technology()) {
            case LANGIUM -> List.of(
                    new CommandSpec("langium-dependencies", "Install pinned Langium project dependencies", npmDependencies),
                    new CommandSpec("langium-build", "Generate and compile Langium project", List.of(npm(), "run", "build")),
                    new CommandSpec("langium-tests", "Run generated Langium smoke tests", List.of(npm(), "test", "--", "--run")));
            case XTEXT -> List.of(
                    new CommandSpec("xtext-generate", "Run the pinned Xtext MWE2 language generator",
                            List.of(mvn(), "-B", "-q", "-N", "generate-sources", "-Djava-21")),
                    new CommandSpec("xtext-build", "Compile generated Xtext runtime, IDE and tests",
                            List.of(mvn(), "-B", "-q", "test", "-Djava-21")));
            case ANTLR -> "TypeScript".equalsIgnoreCase(profile.targetLanguage()) ? List.of(
                    new CommandSpec("antlr-ts-generate", "Generate TypeScript parser with the official pinned ANTLR tool",
                            List.of(mvn(), "-B", "-q", "-f", "generator-pom.xml", "generate-sources")),
                    new CommandSpec("antlr-ts-dependencies", "Install pinned ANTLR TypeScript project dependencies", npmDependencies),
                    new CommandSpec("antlr-ts-build", "Compile generated ANTLR TypeScript project", List.of(npm(), "run", "build")),
                    new CommandSpec("antlr-ts-tests", "Run ANTLR TypeScript smoke tests", List.of(npm(), "test", "--", "--run")))
                    : List.of(new CommandSpec("antlr-java-build", "Generate, compile and test ANTLR Java project",
                            List.of(mvn(), "-B", "-q", "test")));
        };
    }

    /** Descripción: Ejecuta la operación «captureNativeEvidence» correspondiente a la responsabilidad de CaptureBudget. Entradas: root: ruta o ubicación utilizada por la operación; descriptor: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private CaptureResult captureNativeEvidence(Path root, GrammarRuntimeDescriptor descriptor) {
        Set<String> runtimeEntries = new LinkedHashSet<>();
        addPortableEntry(runtimeEntries, descriptor.browserWorkerEntry());
        String additional = descriptor.settings().getOrDefault("portableRuntimeEntries", "");
        for(String value : additional.split(",")) addPortableEntry(runtimeEntries, value);
        if(runtimeEntries.size() > properties.getNativeCaptureMaximumFiles()) {
            throw new IllegalStateException("Native runtime file count exceeds configured capture limit");
        }
        CaptureBudget budget = new CaptureBudget(properties.getNativeCaptureMaximumBytes());
        Map<String, String> runtimeFiles = new LinkedHashMap<>();
        for(String entry : runtimeEntries) runtimeFiles.put(entry, captureUtf8File(root, entry, budget, true));

        Map<String, String> reproducibilityFiles = new LinkedHashMap<>();
        Path packageLock = root.resolve(PACKAGE_LOCK).normalize();
        if(Files.exists(packageLock, LinkOption.NOFOLLOW_LINKS)) {
            reproducibilityFiles.put(PACKAGE_LOCK, captureUtf8File(root, PACKAGE_LOCK, budget, false));
        }
        if(runtimeFiles.size() + reproducibilityFiles.size() > properties.getNativeCaptureMaximumFiles()) {
            throw new IllegalStateException("Native captured file count exceeds configured limit");
        }
        return new CaptureResult(Map.copyOf(runtimeFiles), Map.copyOf(reproducibilityFiles), runtimeEntries.size());
    }

    /** Descripción: Ejecuta la operación «captureUtf8File» correspondiente a la responsabilidad de CaptureBudget. Entradas: root: ruta o ubicación utilizada por la operación; portablePath: ruta o ubicación utilizada por la operación; budget: dato de entrada necesario para la operación; required: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String captureUtf8File(Path root, String portablePath, CaptureBudget budget, boolean required) {
        String safe = GrammarPathPolicy.requirePortableRelative(portablePath, "nativeCapture.path");
        Path target = root.resolve(safe).normalize();
        if(!target.startsWith(root)) throw new IllegalStateException("Native capture path escapes isolated workspace: " + safe);
        if(!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            if(required) throw new IllegalStateException("Declared portable runtime was not materialized: " + safe);
            return "";
        }
        if(Files.isSymbolicLink(target) || !Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalStateException("Native captured artifact must be a regular non-symlink file: " + safe);
        }
        try {
            byte[] content=com.example.editor.io.BoundedIo.read(target,properties.getNativeCaptureMaximumFileBytes());
            budget.consume(content.length,safe);
            return StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(content)).toString();
        } catch(IOException exception) {
            throw new IllegalStateException("Could not capture native artifact as UTF-8 text: " + safe, exception);
        }
    }

    /** Descripción: Ejecuta la operación «addPortableEntry» correspondiente a la responsabilidad de CaptureBudget. Entradas: entries: colección de elementos que debe procesarse; value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private static void addPortableEntry(Set<String> entries, String value) {
        String normalized = value == null ? "" : value.trim().replace('\\', '/');
        while(normalized.startsWith("./")) normalized = normalized.substring(2);
        if(!normalized.isBlank()) entries.add(GrammarPathPolicy.requirePortableRelative(normalized, "runtimeDescriptor.portableEntry"));
    }

    /** Descripción: Ejecuta la operación «materialize» correspondiente a la responsabilidad de CaptureBudget. Entradas: root: ruta o ubicación utilizada por la operación; project: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private static void materialize(Path root, GeneratedGrammarProject project) {
        try {
            for(var file : project.files().entrySet()) {
                String portable = GrammarPathPolicy.requirePortableRelative(file.getKey(), "generatedProject.path");
                Path target = root.resolve(portable).normalize();
                if(!target.startsWith(root)) throw new IllegalArgumentException("Generated path escapes native workspace: " + file.getKey());
                Files.createDirectories(target.getParent());
                Files.writeString(target, file.getValue(), StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING);
            }
        } catch(IOException exception) {
            throw new IllegalStateException("Could not materialize grammar native workspace", exception);
        }
    }

    /** Descripción: Ejecuta la operación «nativeEnvironment» correspondiente a la responsabilidad de CaptureBudget. Entradas: heapMegabytes: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static Map<String, String> nativeEnvironment(int heapMegabytes) {
        String heap = "-Xmx" + Math.max(64, heapMegabytes) + "m";
        return Map.of("MAVEN_OPTS", heap + " -Dfile.encoding=UTF-8", "NODE_OPTIONS",
                "--max-old-space-size=" + Math.max(64, heapMegabytes));
    }

    /** Descripción: Ejecuta la operación «bounded» correspondiente a la responsabilidad de CaptureBudget. Entradas: diagnostics: dato de entrada necesario para la operación; stderr: dato de entrada necesario para la operación; limit: dato de entrada necesario para la operación; maximumChars: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static List<String> bounded(List<String> diagnostics, String stderr, int limit, int maximumChars) {
        List<String> result = new ArrayList<>();
        if(diagnostics != null) for(String item : diagnostics) result.add(cut(item, maximumChars));
        if(stderr != null && !stderr.isBlank()) result.add(cut(stderr, maximumChars));
        return result.stream().limit(Math.max(1, limit)).toList();
    }

    /** Descripción: Ejecuta la operación «cut» correspondiente a la responsabilidad de CaptureBudget. Entradas: value: valor de entrada que debe procesarse; maximumChars: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String cut(String value, int maximumChars) {
        String safe = value == null ? "" : value;
        int max = Math.max(256, maximumChars);
        return safe.length() > max ? safe.substring(0, max) + "…" : safe;
    }

    /** Descripción: Ejecuta la operación «byteSize» correspondiente a la responsabilidad de CaptureBudget. Entradas: files: archivo o contenido que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static long byteSize(Map<String, String> files) {
        return files.values().stream().mapToLong(value -> value.getBytes(StandardCharsets.UTF_8).length).sum();
    }

    /** Descripción: Ejecuta la operación «elapsedMillis» correspondiente a la responsabilidad de CaptureBudget. Entradas: started: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static long elapsedMillis(long started) {
        return Math.max(0L, (System.nanoTime() - started) / 1_000_000L);
    }

    /** Descripción: Ejecuta la operación «npm» correspondiente a la responsabilidad de CaptureBudget. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private static String npm() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win") ? "npm.cmd" : "npm";
    }

    /** Descripción: Ejecuta la operación «mvn» correspondiente a la responsabilidad de CaptureBudget. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private static String mvn() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win") ? "mvn.cmd" : "mvn";
    }

    private record CommandSpec(String id, String description, List<String> command) { }
    private record CaptureResult(Map<String, String> runtimeFiles, Map<String, String> reproducibilityFiles,
            int requestedFiles) { }

    private static final class CaptureBudget {
        private final long maximumBytes;
        private long consumed;
        /** Descripción: Inicializa una instancia de CaptureBudget con sus dependencias y datos de entrada. Entradas: maximumBytes: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ private CaptureBudget(long maximumBytes) { this.maximumBytes = Math.max(1024L, maximumBytes); }
        /** Descripción: Ejecuta la operación «consume» correspondiente a la responsabilidad de CaptureBudget. Entradas: size: dato de entrada necesario para la operación; path: ruta o ubicación utilizada por la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void consume(long size, String path) {
            consumed = Math.addExact(consumed, Math.max(0L, size));
            if(consumed > maximumBytes) throw new IllegalStateException("Native captured artifacts exceed total byte limit at: " + path);
        }
    }
}
