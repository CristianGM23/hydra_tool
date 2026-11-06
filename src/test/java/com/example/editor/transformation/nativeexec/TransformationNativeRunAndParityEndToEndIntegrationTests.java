package com.example.editor.transformation.nativeexec;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.editor.artifact.transaction.HydraArtifactTransactionService;
import com.example.editor.config.StorageProperties;
import com.example.editor.metamodel.application.CanonicalMetamodelApplicationService;
import com.example.editor.metamodel.canonical.CanonicalMetamodelProperties;
import com.example.editor.metamodel.canonical.JsonCanonicalMetamodelRepository;
import com.example.editor.metamodel.validation.MetamodelValidationFingerprint;
import com.example.editor.metamodel.validation.NeutralMetamodelValidator;
import com.example.editor.persistence.LocalCanonicalDocumentStoreFactory;
import com.example.editor.security.process.HydraExternalProcessPolicy;
import com.example.editor.security.process.HydraExternalProcessSecurityProperties;
import com.example.editor.security.process.HydraProcessTreeTerminator;
import com.example.editor.security.process.HydraTemporaryWorkspace;
import com.example.editor.semantic.expression.ExpressionDependencyAnalyzer;
import com.example.editor.semantic.expression.ExpressionEvaluator;
import com.example.editor.semantic.expression.ExpressionTestFixtures;
import com.example.editor.semantic.expression.ExpressionTypeChecker;
import com.example.editor.testing.fault.HydraFaultInjectionProperties;
import com.example.editor.testing.fault.HydraFaultInjectionService;
import com.example.editor.transformation.TransformationApplicationService;
import com.example.editor.transformation.TransformationDefinitionChecker;
import com.example.editor.transformation.TransformationProperties;
import com.example.editor.transformation.core.TransformationBinding;
import com.example.editor.transformation.core.TransformationBindingKind;
import com.example.editor.transformation.core.TransformationDefinitionDocument;
import com.example.editor.transformation.core.TransformationEndpoint;
import com.example.editor.transformation.core.TransformationEndpointRole;
import com.example.editor.transformation.core.TransformationExecutionDirection;
import com.example.editor.transformation.core.TransformationGenerationRule;
import com.example.editor.transformation.core.TransformationKind;
import com.example.editor.transformation.core.TransformationRule;
import com.example.editor.transformation.core.TransformationTargetPattern;
import com.example.editor.transformation.export.AcceleoTransformationLanguageAdapter;
import com.example.editor.transformation.export.AtlTransformationLanguageAdapter;
import com.example.editor.transformation.export.EglTransformationLanguageAdapter;
import com.example.editor.transformation.export.EtlTransformationLanguageAdapter;
import com.example.editor.transformation.export.GoTransformationLanguageAdapter;
import com.example.editor.transformation.export.JavaScriptTransformationLanguageAdapter;
import com.example.editor.transformation.export.JavaTransformationLanguageAdapter;
import com.example.editor.transformation.export.PythonTransformationLanguageAdapter;
import com.example.editor.transformation.export.TransformationExportLanguage;
import com.example.editor.transformation.export.TransformationExportService;
import com.example.editor.transformation.export.TransformationLanguageAdapterRegistry;
import com.example.editor.transformation.export.TypeScriptTransformationLanguageAdapter;
import com.example.editor.transformation.export.XtendTransformationLanguageAdapter;
import com.example.editor.transformation.parity.TransformationParityComparator;
import com.example.editor.transformation.parity.TransformationParityDifference;
import com.example.editor.transformation.parity.TransformationParityReport;
import com.example.editor.transformation.parity.TransformationParityRequest;
import com.example.editor.transformation.parity.TransformationParityService;
import com.example.editor.transformation.parity.TransformationParityStatus;
import com.example.editor.transformation.persistence.JsonTransformationRepository;
import com.example.editor.transformation.runtime.CanonicalTransformationRuntime;
import com.example.editor.transformation.runtime.TransformationPlanCompiler;
import com.example.editor.transformation.runtime.TransformationRuntimeService;
import com.example.editor.transformation.sync.JsonTransformationSynchronizationSessionRepository;
import com.example.editor.transformation.testing.persistence.JsonTransformationTestCaseRepository;
import com.example.editor.transformation.text.CanonicalTextGenerationRuntime;
import com.example.editor.transformation.text.TransformationTextGenerationService;
import com.example.editor.validation.model.CanonicalModelDocument;
import com.example.editor.validation.model.CanonicalModelElement;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Evidencia real, sin mocks y sin toolchains nativas, de ejecutar una exportacion mediante un
 * proceso puente real y comprobar la paridad con el motor de HYDRA
 * (transformation_export_native.run y transformation_parity.ev/cp/ru/kd/cm). Servicios de
 * exportacion, runtime, generacion de texto, comparador, ejecutor y politica de procesos reales;
 * el puente es una JVM hija real (fixture de esta prueba) que implementa por su cuenta la
 * transformacion identidad M2M y el renderizado M2T. Las huellas esperadas se recalculan en la
 * prueba con SHA-256 sobre el formato literal del resultado esperado.
 */
class TransformationNativeRunAndParityEndToEndIntegrationTests {
    private static final String ALICE = "object:alice";
    private static final String BOB = "object:bob";
    private static final String NAME = ExpressionTestFixtures.NAME;
    private static final String NAME_TOKEN = "{{attr:" + NAME + "}}";

    @Test
    @DisplayName("transformation_export_native.run + transformation_parity.ru/kd/cm: M2M identidad con puente real -> MATCHED con la huella esperada")
    void m2mIdentityThroughARealBridgeProcessMatchesTheCanonicalResult(@TempDir Path temporary) throws Exception {
        Env env = Env.open(temporary);
        Path marker = temporary.resolve("bridge-identity.pid");
        env.properties().setAtlCommand(bridge("identity", marker));

        TransformationNativeParityReport report = env.nativeParity().execute(new TransformationNativeParityRequest(
                m2m(false), TransformationExportLanguage.ATL, sourceModel()));

        assertThat(report.execution().status()).isEqualTo(TransformationNativeRuntimeStatus.SUCCEEDED);
        assertThat(report.execution().exitCode()).isZero();
        assertThat(report.kind()).isEqualTo(TransformationKind.M2M);
        // Un proceso real distinto de la JVM de la prueba dejo su PID en el fichero marcador.
        assertThat(Long.parseLong(Files.readString(marker).trim())).isNotEqualTo(ProcessHandle.current().pid());
        TransformationParityReport parity = report.parity();
        assertThat(parity.status()).isEqualTo(TransformationParityStatus.MATCHED);
        assertThat(parity.compatible()).isTrue();
        assertThat(parity.matched()).isTrue();
        assertThat(parity.differences()).isEmpty();
        String expected = modelFingerprint("library", List.of("Alice", "Bob"));
        assertThat(parity.canonicalFingerprint()).isEqualTo(expected);
        assertThat(parity.nativeFingerprint()).isEqualTo(expected);
        // La referencia canonica se ejecuta en memoria: no se persiste ninguna definicion.
        assertThat(filesUnder(temporary.resolve("transformations"))).isEmpty();
    }

    @Test
    @DisplayName("transformation_parity.cm: un puente que altera un valor produce MISMATCHED con la diferencia y su ruta")
    void aBridgeThatAltersAValueIsReportedAsMismatchedWithThePath(@TempDir Path temporary) throws Exception {
        Env env = Env.open(temporary);
        env.properties().setAtlCommand(bridge("mutate", temporary.resolve("bridge-mutate.pid")));

        TransformationNativeParityReport report = env.nativeParity().execute(new TransformationNativeParityRequest(
                m2m(false), TransformationExportLanguage.ATL, sourceModel()));

        assertThat(report.execution().status()).isEqualTo(TransformationNativeRuntimeStatus.SUCCEEDED);
        TransformationParityReport parity = report.parity();
        assertThat(parity.status()).isEqualTo(TransformationParityStatus.MISMATCHED);
        assertThat(parity.matched()).isFalse();
        assertThat(parity.compatible()).isTrue();
        assertThat(parity.canonicalFingerprint()).isEqualTo(modelFingerprint("library", List.of("Alice", "Bob")));
        assertThat(parity.nativeFingerprint()).isEqualTo(modelFingerprint("library", List.of("ALICE", "BOB")));
        assertThat(parity.differences()).allSatisfy(difference -> assertThat(difference.path()).startsWith("$.targetModel."));
        assertThat(parity.differences()).anySatisfy(difference -> {
            assertThat(difference.path()).startsWith("$.targetModel.elements[").contains("5:Alice");
            assertThat(difference.expected()).isEqualTo("1");
            assertThat(difference.actual()).isEqualTo("0");
        });
        assertThat(parity.differences()).anySatisfy(difference -> {
            assertThat(difference.path()).startsWith("$.targetModel.elements[").contains("5:ALICE");
            assertThat(difference.expected()).isEqualTo("0");
            assertThat(difference.actual()).isEqualTo("1");
        });
    }

    @Test
    @DisplayName("transformation_parity.kd/cm: M2T compara ficheros (coincide y, con contenido alterado, difiere)")
    void m2tComparesGeneratedFilesAndDetectsATamperedFile(@TempDir Path temporary) throws Exception {
        Env env = Env.open(temporary);
        env.properties().setJavaCommand(bridge("m2t", temporary.resolve("bridge-m2t.pid")));
        TransformationNativeParityReport matched = env.nativeParity().execute(new TransformationNativeParityRequest(
                m2t(), TransformationExportLanguage.JAVA, sourceModel()));

        assertThat(matched.execution().status()).isEqualTo(TransformationNativeRuntimeStatus.SUCCEEDED);
        assertThat(matched.kind()).isEqualTo(TransformationKind.M2T);
        assertThat(matched.parity().status()).isEqualTo(TransformationParityStatus.MATCHED);
        String expected = artifactsFingerprint(Map.of("out/Alice.txt", "Hello Alice (class:Person)", "out/Bob.txt", "Hello Bob (class:Person)"));
        assertThat(matched.parity().canonicalFingerprint()).isEqualTo(expected);
        assertThat(matched.parity().nativeFingerprint()).isEqualTo(expected);

        env.properties().setJavaCommand(bridge("m2t-tamper", temporary.resolve("bridge-m2t-tamper.pid")));
        TransformationNativeParityReport tampered = env.nativeParity().execute(new TransformationNativeParityRequest(
                m2t(), TransformationExportLanguage.JAVA, sourceModel()));

        assertThat(tampered.parity().status()).isEqualTo(TransformationParityStatus.MISMATCHED);
        assertThat(tampered.parity().canonicalFingerprint()).isEqualTo(expected);
        assertThat(tampered.parity().nativeFingerprint()).isEqualTo(
                artifactsFingerprint(Map.of("out/Alice.txt", "Hello Alice (class:Person)!", "out/Bob.txt", "Hello Bob (class:Person)!")));
        assertThat(tampered.parity().differences()).isNotEmpty()
                .allSatisfy(difference -> assertThat(difference.path()).startsWith("$.artifacts["));
    }

    @Test
    @DisplayName("transformation_parity.ev: lenguaje/tipo no admitido o sin modelo de origen -> INVALID_EVIDENCE sin lanzar el puente")
    void unsupportedLanguageKindOrMissingSourceModelIsInvalidEvidenceWithoutLaunchingTheBridge(@TempDir Path temporary) {
        Env env = Env.open(temporary);
        Path marker = temporary.resolve("bridge-must-not-run.pid");
        env.properties().setAtlCommand(bridge("fail", marker));
        env.properties().setAcceleoCommand(bridge("fail", marker));

        TransformationNativeParityReport acceleoWithM2m = env.nativeParity().execute(new TransformationNativeParityRequest(
                m2m(false), TransformationExportLanguage.ACCELEO, sourceModel()));
        TransformationNativeParityReport atlWithM2t = env.nativeParity().execute(new TransformationNativeParityRequest(
                m2t(), TransformationExportLanguage.ATL, sourceModel()));
        TransformationNativeParityReport reverseOnUnidirectional = env.nativeParity().execute(new TransformationNativeParityRequest(
                m2m(false), TransformationExportLanguage.ATL, TransformationExecutionDirection.REVERSE, sourceModel()));
        TransformationParityReport withoutSource = env.parity().verify(new TransformationParityRequest(
                m2m(false), TransformationExportLanguage.ATL, null, null, List.of()));
        TransformationParityReport acceleoDirect = env.parity().verify(new TransformationParityRequest(
                m2m(false), TransformationExportLanguage.ACCELEO, sourceModel(), null, List.of()));

        for(TransformationNativeParityReport report : List.of(acceleoWithM2m, atlWithM2t, reverseOnUnidirectional)) {
            assertThat(report.execution().status()).isEqualTo(TransformationNativeRuntimeStatus.NOT_RUN);
            assertThat(report.parity().status()).isEqualTo(TransformationParityStatus.INVALID_EVIDENCE);
            assertThat(report.parity().matched()).isFalse();
            assertThat(report.parity().differences()).extracting(TransformationParityDifference::path).containsExactly("$.direction");
        }
        assertThat(withoutSource.status()).isEqualTo(TransformationParityStatus.INVALID_EVIDENCE);
        assertThat(withoutSource.differences()).extracting(TransformationParityDifference::path).containsExactly("$.sourceModel");
        assertThat(acceleoDirect.status()).isEqualTo(TransformationParityStatus.INVALID_EVIDENCE);
        assertThat(acceleoDirect.differences()).extracting(TransformationParityDifference::path).containsExactly("$.language");
        assertThat(Files.exists(marker)).as("el puente no debio lanzarse").isFalse();
    }

    @Test
    @DisplayName("transformation_parity.cp: una exportacion incompatible devuelve EXPORT_BLOCKED y no lanza ningun proceso")
    void blockedExportIsExportBlockedWithoutLaunchingAProcess(@TempDir Path temporary) throws Exception {
        Env env = Env.open(temporary);
        Path marker = temporary.resolve("bridge-must-not-run.pid");
        env.properties().setAtlCommand(bridge("fail", marker));

        TransformationNativeParityReport report = env.nativeParity().execute(new TransformationNativeParityRequest(
                m2m(true), TransformationExportLanguage.ATL, sourceModel()));

        assertThat(report.execution().status()).isEqualTo(TransformationNativeRuntimeStatus.NOT_RUN);
        assertThat(report.execution().diagnostics()).containsExactly("Native execution was not started because export compatibility is blocked");
        assertThat(report.parity().status()).isEqualTo(TransformationParityStatus.EXPORT_BLOCKED);
        assertThat(report.parity().compatible()).isFalse();
        assertThat(report.parity().matched()).isFalse();
        assertThat(report.parity().exportDiagnostics()).extracting(diagnostic -> diagnostic.code()).contains("GUARD_NOT_NATIVE");
        assertThat(Files.exists(marker)).as("el puente no debio lanzarse").isFalse();
    }

    @Test
    @DisplayName("transformation_export_native.run: motor no configurado -> NOT_CONFIGURED sin informe de paridad")
    void unconfiguredEngineIsNotConfiguredAndProducesNoParityReport(@TempDir Path temporary) {
        Env env = Env.open(temporary);

        TransformationNativeParityReport report = env.nativeParity().execute(new TransformationNativeParityRequest(
                m2m(false), TransformationExportLanguage.ETL, sourceModel()));

        assertThat(report.execution().status()).isEqualTo(TransformationNativeRuntimeStatus.NOT_CONFIGURED);
        assertThat(report.parity()).isNull();
        assertThat(report.language()).isEqualTo(TransformationExportLanguage.ETL);
    }

    @Test
    @DisplayName("transformation_export_native.run: un puente que no termina se corta por tiempo (TIMED_OUT) y su proceso queda terminado")
    void aBridgeThatNeverEndsIsTimedOutAndItsProcessIsTerminated(@TempDir Path temporary) throws Exception {
        Env env = Env.open(temporary);
        Path marker = temporary.resolve("bridge-hang.pid");
        env.properties().setDefaultTimeoutSeconds(4);
        env.properties().setAtlCommand(bridge("hang", marker));

        TransformationNativeParityReport report = env.nativeParity().execute(new TransformationNativeParityRequest(
                m2m(false), TransformationExportLanguage.ATL, sourceModel()));

        assertThat(report.execution().status()).isEqualTo(TransformationNativeRuntimeStatus.TIMED_OUT);
        assertThat(report.execution().diagnostics()).contains("Native bridge exceeded 4 seconds");
        assertThat(report.parity()).isNull();
        long pid = Long.parseLong(Files.readString(marker).trim());
        assertThat(pid).isNotEqualTo(ProcessHandle.current().pid());
        ProcessHandle.of(pid).ifPresent(handle -> {
            try { handle.onExit().get(10, TimeUnit.SECONDS); }
            catch(Exception exception) { throw new AssertionError("The timed-out bridge process is still alive: " + pid, exception); }
        });
        assertThat(ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false)).isFalse();
    }

    // ----------------------------------------------------------------------- oraculos independientes

    private static String scalar(String value) { return value.length() + ":" + value; }

    private static String sha256(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    /** Recalcula la huella semantica esperada de un modelo de elementos Person con solo el atributo name (todos raiz). */
    private static String modelFingerprint(String metamodelId, List<String> names) throws Exception {
        List<String> signatures = names.stream()
                .map(name -> "class=" + scalar("class:Person") + ";attributes={" + scalar(NAME) + ":" + scalar(name) + "}").sorted().toList();
        return sha256("metamodel=" + metamodelId + "\nroots=" + String.join("\n", signatures)
                + "\nelements=" + String.join("\n", signatures) + "\nrelations=");
    }

    private static String artifactsFingerprint(Map<String, String> filesByPath) throws Exception {
        List<String> entries = new ArrayList<>();
        filesByPath.forEach((path, content) -> entries.add("path=" + scalar(path) + ";content=" + scalar(content)));
        return sha256(String.join("\n", entries.stream().sorted().toList()));
    }

    private static List<Path> filesUnder(Path directory) throws IOException {
        if(!Files.exists(directory)) return List.of();
        try(Stream<Path> walk = Files.walk(directory)) { return walk.filter(path -> !path.equals(directory)).toList(); }
    }

    // ----------------------------------------------------------------------------------- fixtures

    private static List<String> bridge(String mode, Path marker) {
        String javaBinary = Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win") ? "java.exe" : "java").toString();
        Path base = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        String classpath = java.util.Arrays.stream(System.getProperty("java.class.path").split(Pattern.quote(java.io.File.pathSeparator)))
                .map(Path::of).map(path -> path.isAbsolute() ? path : base.resolve(path).normalize())
                .map(Path::toString).collect(java.util.stream.Collectors.joining(java.io.File.pathSeparator));
        return List.of(javaBinary, "-cp", classpath, ParityBridgeMain.class.getName(), mode, marker.toAbsolutePath().toString());
    }

    private static CanonicalModelDocument sourceModel() {
        CanonicalModelElement alice = new CanonicalModelElement(ALICE, ExpressionTestFixtures.PERSON, Map.of(NAME, "Alice"), Map.of());
        CanonicalModelElement bob = new CanonicalModelElement(BOB, ExpressionTestFixtures.PERSON, Map.of(NAME, "Bob"), Map.of());
        return new CanonicalModelDocument("1.0", "parity-source", ExpressionTestFixtures.METAMODEL_ID, "", "Parity source",
                List.of(ALICE, BOB), List.of(alice, bob), List.of(), Map.of());
    }

    private static TransformationDefinitionDocument m2m(boolean guarded) {
        TransformationBinding binding = new TransformationBinding("binding:name", "person", NAME, TransformationBindingKind.DIRECT,
                NAME, null, null, null, Map.of());
        TransformationRule rule = new TransformationRule("rule:person", "Person to Person", true, "source", ExpressionTestFixtures.PERSON,
                "personSource", guarded ? ExpressionTestFixtures.positiveAge() : null,
                List.of(new TransformationTargetPattern("target:person", "target", ExpressionTestFixtures.PERSON, "person", Map.of())),
                List.of(binding), Map.of());
        return new TransformationDefinitionDocument("1.0", "transformation-parity-m2m", "Parity M2M", TransformationKind.M2M,
                List.of(new TransformationEndpoint("source", TransformationEndpointRole.SOURCE, ExpressionTestFixtures.METAMODEL_ID, "", "source", true, Map.of())),
                List.of(new TransformationEndpoint("target", TransformationEndpointRole.TARGET, ExpressionTestFixtures.METAMODEL_ID, "", "target", true, Map.of())),
                List.of(rule), List.of(), null, null, null, Map.of());
    }

    private static TransformationDefinitionDocument m2t() {
        TransformationGenerationRule rule = new TransformationGenerationRule("gen:person", "Generate person", true, "source",
                ExpressionTestFixtures.PERSON, null, "out/" + NAME_TOKEN + ".txt", "Hello " + NAME_TOKEN + " ({{classId}})", null, Map.of());
        return new TransformationDefinitionDocument("1.0", "transformation-parity-m2t", "Parity M2T", TransformationKind.M2T,
                List.of(new TransformationEndpoint("source", TransformationEndpointRole.SOURCE, ExpressionTestFixtures.METAMODEL_ID, "", "source", true, Map.of())),
                List.of(), List.of(), List.of(rule), null, null, null, Map.of());
    }

    private record Env(TransformationNativeParityService nativeParity, TransformationParityService parity,
            TransformationNativeRuntimeProperties properties) {
        static Env open(Path temporary) {
            JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();
            StorageProperties storage = new StorageProperties(temporary.resolve("metamodels"), temporary.resolve("models"),
                    temporary.resolve("notations"), temporary.resolve("validations"), temporary.resolve("quickfixes"),
                    temporary.resolve("generators"), temporary.resolve("assets"), temporary.resolve("exports"), true);
            CanonicalMetamodelApplicationService metamodels = new CanonicalMetamodelApplicationService(
                    new JsonCanonicalMetamodelRepository(storage, new CanonicalMetamodelProperties(), mapper), new NeutralMetamodelValidator());
            metamodels.create(ExpressionTestFixtures.metamodel());

            TransformationProperties transformationProperties = new TransformationProperties();
            transformationProperties.setDirectory(temporary.resolve("transformations"));
            transformationProperties.setTestDirectory(temporary.resolve("transformation-tests"));
            transformationProperties.setSynchronizationSessionDirectory(temporary.resolve("transformation-sync-sessions"));
            transformationProperties.setSynchronizationHistoryDirectory(temporary.resolve("transformation-sync-history"));

            ExpressionTypeChecker typeChecker = new ExpressionTypeChecker(new ExpressionDependencyAnalyzer());
            TransformationDefinitionChecker checker = new TransformationDefinitionChecker(typeChecker);
            HydraArtifactTransactionService transactions = new HydraArtifactTransactionService(
                    temporary.resolve("artifact-transactions").toString(), new HydraFaultInjectionService(new HydraFaultInjectionProperties()));
            TransformationApplicationService transformations = new TransformationApplicationService(
                    new JsonTransformationRepository(transformationProperties, mapper), metamodels,
                    new MetamodelValidationFingerprint(mapper), checker,
                    new JsonTransformationTestCaseRepository(transformationProperties, mapper, new LocalCanonicalDocumentStoreFactory()),
                    new JsonTransformationSynchronizationSessionRepository(transformationProperties, mapper, new LocalCanonicalDocumentStoreFactory()),
                    transactions);
            TransformationRuntimeService runtime = new TransformationRuntimeService(transformations, metamodels,
                    new TransformationPlanCompiler(checker), new CanonicalTransformationRuntime(new ExpressionEvaluator(typeChecker)));
            TransformationTextGenerationService text = new TransformationTextGenerationService(transformations, metamodels, checker,
                    new CanonicalTextGenerationRuntime(new ExpressionEvaluator(typeChecker)));

            ObjectMapper plain = new ObjectMapper();
            TransformationLanguageAdapterRegistry registry = new TransformationLanguageAdapterRegistry(List.of(
                    new AtlTransformationLanguageAdapter(), new EtlTransformationLanguageAdapter(),
                    new JavaTransformationLanguageAdapter(plain), new XtendTransformationLanguageAdapter(plain),
                    new PythonTransformationLanguageAdapter(plain), new GoTransformationLanguageAdapter(plain),
                    new JavaScriptTransformationLanguageAdapter(plain), new TypeScriptTransformationLanguageAdapter(plain),
                    new AcceleoTransformationLanguageAdapter(), new EglTransformationLanguageAdapter()));
            TransformationExportService exports = new TransformationExportService(registry, checker, metamodels);
            TransformationParityService parity = new TransformationParityService(exports, runtime, text, new TransformationParityComparator());

            TransformationNativeRuntimeProperties properties = new TransformationNativeRuntimeProperties();
            HydraExternalProcessSecurityProperties security = new HydraExternalProcessSecurityProperties();
            security.setTemporaryRoot(temporary.resolve("process-workspaces"));
            security.setMaximumArgumentCharacters(200_000);
            HydraExternalProcessPolicy policy = new HydraExternalProcessPolicy(security);
            HydraTemporaryWorkspace workspaces = new HydraTemporaryWorkspace(policy);
            Executor blocking = Executors.newCachedThreadPool(runnable -> {
                Thread thread = new Thread(runnable, "native-parity-test-io");
                thread.setDaemon(true);
                return thread;
            });
            TransformationNativeRuntimeExecutor executor = new TransformationNativeRuntimeExecutor(properties, policy, workspaces,
                    metamodels, mapper, blocking, new HydraProcessTreeTerminator());
            return new Env(new TransformationNativeParityService(exports, executor, parity), parity, properties);
        }
    }

    /**
     * Puente nativo real (proceso hijo): args = modo, fichero marcador, ruta de la peticion. Escribe su PID en
     * el marcador y, segun el modo, copia el modelo (identity), pone en mayusculas los textos (mutate), genera
     * ficheros desde las reglas M2T (m2t, m2t-tamper), no termina (hang) o falla (fail).
     */
    public static final class ParityBridgeMain {
        private static final Pattern TOKEN = Pattern.compile("\\{\\{\\s*(id|classId|attr:[A-Za-z0-9][A-Za-z0-9._:-]*)\\s*}}");

        private ParityBridgeMain() {}

        public static void main(String[] args) throws Exception {
            String mode = args[0];
            Path marker = Path.of(args[1]);
            String last = args[args.length - 1];
            if("--hydra-describe".equals(last)) return;
            Files.writeString(marker, Long.toString(ProcessHandle.current().pid()));
            if("fail".equals(mode)) throw new IllegalStateException("the bridge must not be launched in this scenario");
            if("hang".equals(mode)) { Thread.sleep(120_000L); return; }

            ObjectMapper mapper = new ObjectMapper();
            Path requestFile = Path.of(last).toAbsolutePath().normalize();
            Path workspace = requestFile.getParent();
            TransformationNativeBridgeRequest request = mapper.readValue(requestFile.toFile(), TransformationNativeBridgeRequest.class);
            CanonicalModelDocument source = mapper.readValue(workspace.resolve(request.sourceModelFile()).toFile(), CanonicalModelDocument.class);
            Path outputDirectory = Files.createDirectories(workspace.resolve(request.outputDirectory()));
            TransformationNativeBridgeResult result;
            if(mode.startsWith("m2t")) {
                JsonNode definition = mapper.readTree(Files.readString(workspace.resolve(request.transformationFile())));
                List<TransformationNativeBridgeArtifact> artifacts = new ArrayList<>();
                int index = 0;
                for(CanonicalModelElement element : source.elements()) {
                    for(JsonNode rule : definition.get("generationRules")) {
                        if(!rule.get("enabled").booleanValue() || !element.classId().equals(rule.get("contextTypeId").asString())) continue;
                        String content = render(rule.get("contentTemplate").asString(), element) + ("m2t-tamper".equals(mode) ? "!" : "");
                        Path file = outputDirectory.resolve("artifacts").resolve((index++) + ".txt");
                        Files.createDirectories(file.getParent());
                        Files.writeString(file, content, StandardCharsets.UTF_8);
                        artifacts.add(new TransformationNativeBridgeArtifact(render(rule.get("outputPathTemplate").asString(), element),
                                workspace.relativize(file).toString().replace('\\', '/')));
                    }
                }
                result = new TransformationNativeBridgeResult(request.schemaVersion(), request.transformationId(), request.language(),
                        request.kind(), request.direction(), "", artifacts, List.of("m2t executed"));
            } else {
                CanonicalModelDocument target = "mutate".equals(mode) ? upperCased(source) : source;
                Path file = outputDirectory.resolve("target-model.json");
                mapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), target);
                result = new TransformationNativeBridgeResult(request.schemaVersion(), request.transformationId(), request.language(),
                        request.kind(), request.direction(), workspace.relativize(file).toString().replace('\\', '/'), List.of(),
                        List.of(mode + " executed"));
            }
            mapper.writerWithDefaultPrettyPrinter().writeValue(workspace.resolve(request.resultFile()).toFile(), result);
        }

        private static CanonicalModelDocument upperCased(CanonicalModelDocument source) {
            List<CanonicalModelElement> elements = source.elements().stream().map(element -> {
                Map<String, Object> attributes = new LinkedHashMap<>();
                element.attributes().forEach((key, value) -> attributes.put(key, value instanceof String text ? text.toUpperCase(Locale.ROOT) : value));
                return new CanonicalModelElement(element.id(), element.classId(), attributes, element.annotations());
            }).toList();
            return new CanonicalModelDocument(source.schemaVersion(), source.id(), source.metamodelId(), source.metamodelEtag(), source.name(),
                    source.rootElementIds(), elements, source.relations(), source.annotations());
        }

        private static String render(String template, CanonicalModelElement element) {
            Matcher matcher = TOKEN.matcher(template);
            StringBuilder out = new StringBuilder();
            while(matcher.find()) {
                String token = matcher.group(1);
                Object value = token.equals("id") ? element.id() : token.equals("classId") ? element.classId()
                        : element.attributes().get(token.substring("attr:".length()));
                matcher.appendReplacement(out, Matcher.quoteReplacement(value == null ? "" : String.valueOf(value)));
            }
            matcher.appendTail(out);
            return out.toString();
        }
    }
}
