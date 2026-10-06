package com.example.editor.transformation.nativeexec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.example.editor.metamodel.application.CanonicalMetamodelApplicationService;
import com.example.editor.security.process.HydraExternalProcessPolicy;
import com.example.editor.security.process.HydraExternalProcessSecurityProperties;
import com.example.editor.security.process.HydraProcessTreeTerminator;
import com.example.editor.security.process.HydraTemporaryWorkspace;
import com.example.editor.transformation.core.TransformationDefinitionDocument;
import com.example.editor.transformation.core.TransformationExecutionDirection;
import com.example.editor.transformation.core.TransformationKind;
import com.example.editor.transformation.export.TransformationExportFidelity;
import com.example.editor.transformation.export.TransformationExportLanguage;
import com.example.editor.transformation.export.TransformationExportResult;
import com.example.editor.validation.model.CanonicalModelDocument;
import tools.jackson.databind.ObjectMapper;

class TransformationNativeRuntimeExecutorTests {
    private final HydraExternalProcessPolicy policy = mock(HydraExternalProcessPolicy.class);
    private final HydraTemporaryWorkspace workspaces = mock(HydraTemporaryWorkspace.class);
    private final CanonicalMetamodelApplicationService metamodels = mock(CanonicalMetamodelApplicationService.class);
    private final ObjectMapper mapper = mock(ObjectMapper.class);
    private final Executor direct = Runnable::run;

    @Test
    void exposesOnlyNativeLanguagesWithoutLeakingConfiguredCommand() {
        TransformationNativeRuntimeProperties properties = new TransformationNativeRuntimeProperties();
        properties.setAtlCommand(List.of("java", "-jar", "C:/private/atl-bridge.jar"));
        TransformationNativeRuntimeExecutor executor = executor(properties);

        var descriptors = executor.runtimes();

        assertThat(descriptors).extracting(TransformationNativeRuntimeDescriptor::language)
                .containsExactly(TransformationExportLanguage.ACCELEO, TransformationExportLanguage.ATL,
                        TransformationExportLanguage.EGL, TransformationExportLanguage.ETL,
                        TransformationExportLanguage.GO, TransformationExportLanguage.JAVA,
                        TransformationExportLanguage.JAVASCRIPT, TransformationExportLanguage.PYTHON,
                        TransformationExportLanguage.TYPESCRIPT, TransformationExportLanguage.XTEND);
        var atl = descriptors.stream().filter(item -> item.language() == TransformationExportLanguage.ATL).findFirst().orElseThrow();
        var java = descriptors.stream().filter(item -> item.language() == TransformationExportLanguage.JAVA).findFirst().orElseThrow();
        var python = descriptors.stream().filter(item -> item.language() == TransformationExportLanguage.PYTHON).findFirst().orElseThrow();
        assertThat(atl.configured()).isTrue();
        assertThat(atl.parityReady()).isTrue();
        assertThat(atl.mode()).isEqualTo(TransformationNativeRuntimeMode.NATIVE_EXPORT);
        assertThat(atl.supportedKinds()).containsExactly(TransformationKind.M2M);
        assertThat(atl.supportedDirections()).containsExactly(TransformationExecutionDirection.FORWARD, TransformationExecutionDirection.REVERSE);
        assertThat(atl.fullBidirectionalSynchronization()).isFalse();
        assertThat(atl.incrementalSynchronization()).isFalse();
        assertThat(java.parityReady()).isTrue();
        assertThat(java.mode()).isEqualTo(TransformationNativeRuntimeMode.NATIVE_EXPORT);
        assertThat(java.supportedKinds()).containsExactly(TransformationKind.M2M, TransformationKind.M2T);
        assertThat(java.supportedDirections()).containsExactly(TransformationExecutionDirection.FORWARD, TransformationExecutionDirection.REVERSE);
        assertThat(python.parityReady()).isTrue();
        assertThat(python.supportedKinds()).containsExactly(TransformationKind.M2M, TransformationKind.M2T);
        assertThat(python.supportedDirections()).containsExactly(TransformationExecutionDirection.FORWARD, TransformationExecutionDirection.REVERSE);
        assertThat(descriptors.toString()).doesNotContain("private").doesNotContain("atl-bridge.jar");
    }

    @Test
    void unconfiguredRuntimeFailsClosedBeforeCreatingWorkspace() {
        TransformationNativeRuntimeProperties properties = new TransformationNativeRuntimeProperties();
        TransformationNativeRuntimeExecutor executor = executor(properties);

        var outcome = executor.execute(document(), TransformationExportLanguage.ATL, sourceModel(), compatibleExport());

        assertThat(outcome.report().status()).isEqualTo(TransformationNativeRuntimeStatus.NOT_CONFIGURED);
        verify(workspaces, never()).create(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void xtendIsIndependentNativeCapabilityButStillRequiresACompatibleManagedBridge() {
        TransformationNativeRuntimeProperties properties = new TransformationNativeRuntimeProperties();
        TransformationNativeRuntimeExecutor executor = executor(properties);
        TransformationExportResult export = new TransformationExportResult("native.parity", TransformationExportLanguage.XTEND,
                TransformationExportFidelity.EXACT, true, List.of(), List.of());

        var outcome = executor.execute(document(), TransformationExportLanguage.XTEND, sourceModel(), export);

        assertThat(outcome.report().status()).isEqualTo(TransformationNativeRuntimeStatus.NOT_CONFIGURED);
        verify(workspaces, never()).create(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void disabledRuntimeFailsClosedEvenWhenCommandExists() {
        TransformationNativeRuntimeProperties properties = new TransformationNativeRuntimeProperties();
        properties.setEnabled(false);
        properties.setAtlCommand(List.of("java", "-jar", "bridge.jar"));
        TransformationNativeRuntimeExecutor executor = executor(properties);

        var outcome = executor.execute(document(), TransformationExportLanguage.ATL, sourceModel(), compatibleExport());

        assertThat(outcome.report().status()).isEqualTo(TransformationNativeRuntimeStatus.DISABLED);
        verify(workspaces, never()).create(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void executesFilesystemProtocolThroughTheSharedExternalProcessSecurityBoundary(@TempDir Path temporaryRoot) {
        TransformationNativeRuntimeProperties properties = new TransformationNativeRuntimeProperties();
        String javaExecutable = Path.of(System.getProperty("java.home"), "bin", isWindows() ? "java.exe" : "java").toString();
        properties.setAtlCommand(List.of(javaExecutable, "-cp", absoluteClasspath(), NativeRuntimeBridgeFixtureMain.class.getName()));
        HydraExternalProcessSecurityProperties securityProperties = new HydraExternalProcessSecurityProperties();
        securityProperties.setTemporaryRoot(temporaryRoot);
        securityProperties.setMaximumArgumentCharacters(200_000);
        HydraExternalProcessPolicy realPolicy = new HydraExternalProcessPolicy(securityProperties);
        HydraTemporaryWorkspace realWorkspaces = new HydraTemporaryWorkspace(realPolicy);
        TransformationNativeRuntimeExecutor executor = new TransformationNativeRuntimeExecutor(properties, realPolicy, realWorkspaces,
                metamodels, new ObjectMapper(), direct, new HydraProcessTreeTerminator());

        var outcome = executor.execute(document(), TransformationExportLanguage.ATL, sourceModel(), compatibleExport());

        assertThat(outcome.report().status()).isEqualTo(TransformationNativeRuntimeStatus.SUCCEEDED);
        assertThat(outcome.report().exitCode()).isZero();
        assertThat(outcome.targetModel()).isNotNull();
        assertThat(outcome.targetModel().id()).isEqualTo("source.model");
        assertThat(outcome.report().diagnostics()).contains("fixture executed");
    }

    @Test
    void executesReverseDirectionThroughProtocol11(@TempDir Path temporaryRoot) {
        TransformationNativeRuntimeProperties properties = new TransformationNativeRuntimeProperties();
        String javaExecutable = Path.of(System.getProperty("java.home"), "bin", isWindows() ? "java.exe" : "java").toString();
        properties.setAtlCommand(List.of(javaExecutable, "-cp", absoluteClasspath(), NativeRuntimeBridgeFixtureMain.class.getName()));
        HydraExternalProcessSecurityProperties securityProperties = new HydraExternalProcessSecurityProperties();
        securityProperties.setTemporaryRoot(temporaryRoot);
        securityProperties.setMaximumArgumentCharacters(200_000);
        HydraExternalProcessPolicy realPolicy = new HydraExternalProcessPolicy(securityProperties);
        HydraTemporaryWorkspace realWorkspaces = new HydraTemporaryWorkspace(realPolicy);
        TransformationNativeRuntimeExecutor executor = new TransformationNativeRuntimeExecutor(properties, realPolicy, realWorkspaces,
                metamodels, new ObjectMapper(), direct, new HydraProcessTreeTerminator());

        var outcome = executor.execute(document(), TransformationExportLanguage.ATL, TransformationExecutionDirection.REVERSE,
                sourceModel(), compatibleExport());

        assertThat(outcome.report().status()).isEqualTo(TransformationNativeRuntimeStatus.SUCCEEDED);
        assertThat(outcome.report().direction()).isEqualTo(TransformationExecutionDirection.REVERSE);
        assertThat(outcome.targetModel()).isNotNull();
        assertThat(outcome.report().diagnostics()).contains("fixture executed");
    }

    @Test
    void diagnosesBridgeVersionsAndNarrowsCapabilitiesWithoutLeakingCommand(@TempDir Path temporaryRoot) {
        TransformationNativeRuntimeProperties properties = new TransformationNativeRuntimeProperties();
        String javaExecutable = Path.of(System.getProperty("java.home"), "bin", isWindows() ? "java.exe" : "java").toString();
        properties.setAtlCommand(List.of(javaExecutable, "-cp", absoluteClasspath(), NativeRuntimeBridgeFixtureMain.class.getName()));
        HydraExternalProcessSecurityProperties securityProperties = new HydraExternalProcessSecurityProperties();
        securityProperties.setTemporaryRoot(temporaryRoot);
        securityProperties.setMaximumArgumentCharacters(200_000);
        HydraExternalProcessPolicy realPolicy = new HydraExternalProcessPolicy(securityProperties);
        HydraTemporaryWorkspace realWorkspaces = new HydraTemporaryWorkspace(realPolicy);
        TransformationNativeRuntimeExecutor executor = new TransformationNativeRuntimeExecutor(properties, realPolicy, realWorkspaces,
                metamodels, new ObjectMapper(), direct, new HydraProcessTreeTerminator());

        var diagnostic = executor.diagnose(TransformationExportLanguage.ATL);
        var descriptor = executor.runtimes().stream().filter(item -> item.language() == TransformationExportLanguage.ATL).findFirst().orElseThrow();

        assertThat(diagnostic.health()).isEqualTo(TransformationNativeRuntimeHealth.READY);
        assertThat(diagnostic.compatible()).isTrue();
        assertThat(diagnostic.bridgeName()).isEqualTo("fixture-bridge");
        assertThat(diagnostic.bridgeVersion()).isEqualTo("1.2.3");
        assertThat(diagnostic.toolchainName()).isEqualTo("fixture-atl");
        assertThat(diagnostic.toolchainVersion()).isEqualTo("4.0");
        assertThat(diagnostic.protocolVersions()).containsExactly("1.0", "1.1");
        assertThat(diagnostic.supportedDirections()).containsExactly(TransformationExecutionDirection.FORWARD, TransformationExecutionDirection.REVERSE);
        assertThat(descriptor.health()).isEqualTo(TransformationNativeRuntimeHealth.READY);
        assertThat(descriptor.source()).isEqualTo(TransformationNativeRuntimeSource.EXPLICIT_COMMAND);
        assertThat(diagnostic.toString()).doesNotContain(javaExecutable).doesNotContain(absoluteClasspath());
    }

    @Test
    void commandConfigurationDropsBlankArgumentsForEveryRuntimeFamily() {
        TransformationNativeRuntimeProperties properties = new TransformationNativeRuntimeProperties();
        properties.setAtlCommand(List.of(" java ", "", " -jar ", " bridge.jar "));
        properties.setJavaCommand(List.of(" java ", " -jar ", " java-bridge.jar "));
        properties.setPythonCommand(List.of(" python ", " bridge.py "));
        properties.setGoCommand(List.of(" go-bridge "));

        assertThat(properties.commandFor(TransformationExportLanguage.ATL)).containsExactly("java", "-jar", "bridge.jar");
        assertThat(properties.commandFor(TransformationExportLanguage.JAVA)).containsExactly("java", "-jar", "java-bridge.jar");
        assertThat(properties.commandFor(TransformationExportLanguage.PYTHON)).containsExactly("python", "bridge.py");
        assertThat(properties.commandFor(TransformationExportLanguage.GO)).containsExactly("go-bridge");
        assertThat(properties.commandFor(TransformationExportLanguage.XTEND)).isEmpty();
    }

    private boolean isWindows(){return System.getProperty("os.name", "").toLowerCase().contains("win");}
    private String absoluteClasspath(){
        Path base=Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        return java.util.Arrays.stream(System.getProperty("java.class.path").split(java.util.regex.Pattern.quote(java.io.File.pathSeparator)))
                .map(Path::of).map(path->path.isAbsolute()?path:base.resolve(path).normalize())
                .map(Path::toString).collect(java.util.stream.Collectors.joining(java.io.File.pathSeparator));
    }

    private TransformationNativeRuntimeExecutor executor(TransformationNativeRuntimeProperties properties) {
        return new TransformationNativeRuntimeExecutor(properties, policy, workspaces, metamodels, mapper, direct,
                new HydraProcessTreeTerminator());
    }

    private TransformationDefinitionDocument document() {
        return new TransformationDefinitionDocument("1.0", "native.parity", "Native parity", TransformationKind.M2M,
                List.of(), List.of(), List.of(), List.of(), null, null, null, Map.of());
    }

    private CanonicalModelDocument sourceModel() {
        return new CanonicalModelDocument("1.0", "source.model", "library", "", "Source", List.of(), List.of(), List.of(), Map.of());
    }

    private TransformationExportResult compatibleExport() {
        return new TransformationExportResult("native.parity", TransformationExportLanguage.ATL,
                TransformationExportFidelity.SUBSET, true, List.of(), List.of());
    }
    /** Self-contained subprocess fixture so test compilation does not depend on implicit source discovery. */
    public static final class NativeRuntimeBridgeFixtureMain {
        private NativeRuntimeBridgeFixtureMain() {}

        public static void main(String[] args) throws Exception {
            ObjectMapper mapper = new ObjectMapper();
            if (args.length == 1 && "--hydra-describe".equals(args[0])) {
                TransformationNativeBridgeDescription description = new TransformationNativeBridgeDescription("1.0",
                        TransformationExportLanguage.ATL, "fixture-bridge", "1.2.3", "fixture-atl", "4.0",
                        List.of("1.0", "1.1"), List.of(TransformationKind.M2M),
                        List.of(TransformationExecutionDirection.FORWARD, TransformationExecutionDirection.REVERSE), false, false);
                System.out.print(mapper.writeValueAsString(description));
                return;
            }
            if (args.length != 1) throw new IllegalArgumentException("Expected native-request.json path");
            Path requestFile = Path.of(args[0]).toAbsolutePath().normalize();
            Path workspace = requestFile.getParent();
            String requestJson = Files.readString(requestFile);
            TransformationNativeBridgeRequest request = mapper.readValue(requestFile.toFile(), TransformationNativeBridgeRequest.class);
            if ("1.0".equals(request.schemaVersion()) && requestJson.contains("\"direction\""))
                throw new IllegalArgumentException("Protocol 1.0 request must retain the historical forward-only wire shape");
            if ("1.1".equals(request.schemaVersion()) && !requestJson.contains("\"direction\""))
                throw new IllegalArgumentException("Protocol 1.1 request must carry an explicit direction");
            CanonicalModelDocument source = mapper.readValue(workspace.resolve(request.sourceModelFile()).toFile(), CanonicalModelDocument.class);
            Path outputDirectory = workspace.resolve(request.outputDirectory());
            Files.createDirectories(outputDirectory);
            Path target = outputDirectory.resolve("target-model.json");
            mapper.writerWithDefaultPrettyPrinter().writeValue(target.toFile(), source);
            TransformationNativeBridgeResult result = new TransformationNativeBridgeResult(request.schemaVersion(), request.transformationId(),
                    request.language(), request.kind(), request.direction(), workspace.relativize(target).toString(), List.of(), List.of("fixture executed"));
            mapper.writerWithDefaultPrettyPrinter().writeValue(workspace.resolve(request.resultFile()).toFile(), result);
        }
    }

}
