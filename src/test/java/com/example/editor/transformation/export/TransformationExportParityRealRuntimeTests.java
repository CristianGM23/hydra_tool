package com.example.editor.transformation.export;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
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
import com.example.editor.transformation.parity.TransformationParityArtifact;
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
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Evidencia real, sin mocks, de «Exportar a un lenguaje de transformacion» y «Comprobar la
 * paridad con HYDRA» (x_transformation_run_and_sync.exp y .par): el servicio de exportacion real
 * escribe los ficheros de cada lenguaje en un directorio temporal (se releen del disco) y el
 * servicio de paridad real compara el resultado del motor de HYDRA con un resultado esperado
 * escrito a mano (coincide), con uno divergente (no coincide) y con una exportacion bloqueada
 * (no hay evidencia valida). Las huellas esperadas se recalculan en la prueba.
 */
class TransformationExportParityRealRuntimeTests {
    private static final String NAME = ExpressionTestFixtures.NAME;
    private static final ObjectMapper PLAIN = new ObjectMapper();

    @Test
    @DisplayName("x_transformation_run_and_sync.exp: cada lenguaje compatible escribe sus ficheros en disco con el contenido esperado")
    void exportsEachCompatibleLanguageToATemporaryDirectory(@TempDir Path temporary) throws Exception {
        Env env = Env.open(temporary);
        Map<TransformationExportLanguage, String> wrapperPaths = Map.of(
                TransformationExportLanguage.JAVA, "src/main/java/generated/HydraTransformation.java",
                TransformationExportLanguage.XTEND, "src/generated/HydraTransformation.xtend",
                TransformationExportLanguage.PYTHON, "generated/hydra_transformation.py",
                TransformationExportLanguage.GO, "generated/hydra_transformation.go");

        TransformationExportResult atl = env.exports().export(new TransformationExportRequest(m2m(false), TransformationExportLanguage.ATL));
        Path atlDirectory = write(temporary.resolve("out-atl"), atl);
        assertThat(atl.compatible()).isTrue();
        assertThat(Files.readString(atlDirectory.resolve("Parity.atl"))).contains("module Parity;", "rule Person_to_Person",
                "name <- personSource.name");

        TransformationExportResult etl = env.exports().export(new TransformationExportRequest(m2m(false), TransformationExportLanguage.ETL));
        Path etlDirectory = write(temporary.resolve("out-etl"), etl);
        assertThat(etl.compatible()).isTrue();
        assertThat(filesUnder(etlDirectory)).hasSize(1);
        assertThat(Files.readString(filesUnder(etlDirectory).get(0))).contains("transform personSource : SOURCE!Person", "person.name = personSource.name");

        for(TransformationExportLanguage language : List.of(TransformationExportLanguage.JAVA, TransformationExportLanguage.XTEND,
                TransformationExportLanguage.PYTHON, TransformationExportLanguage.GO, TransformationExportLanguage.JAVASCRIPT,
                TransformationExportLanguage.TYPESCRIPT)) {
            TransformationExportResult result = env.exports().export(new TransformationExportRequest(m2m(false), language));
            Path directory = write(temporary.resolve("out-" + language.name().toLowerCase()), result);
            assertThat(result.compatible()).as(language.name()).isTrue();
            assertThat(result.fidelity()).as(language.name()).isEqualTo(TransformationExportFidelity.EXACT);
            assertThat(Files.exists(directory.resolve("transformation.json"))).as(language.name()).isTrue();
            assertThat(PLAIN.readTree(Files.readString(directory.resolve("native/native-plan.json"))).get("rules").get(0).get("ruleId").asString())
                    .as(language.name()).isEqualTo("rule:person");
            if(wrapperPaths.containsKey(language))
                assertThat(Files.exists(directory.resolve(wrapperPaths.get(language)))).as(language.name()).isTrue();
        }

        TransformationExportResult egl = env.exports().export(new TransformationExportRequest(m2t(), TransformationExportLanguage.EGL));
        Path eglDirectory = write(temporary.resolve("out-egl"), egl);
        assertThat(egl.compatible()).isTrue();
        assertThat(filesUnder(eglDirectory)).extracting(path -> path.getFileName().toString())
                .anyMatch(file -> file.endsWith(".egx")).anyMatch(file -> file.endsWith(".egl"));
        TransformationExportResult acceleo = env.exports().export(new TransformationExportRequest(m2t(), TransformationExportLanguage.ACCELEO));
        assertThat(acceleo.compatible()).isTrue();
        assertThat(acceleo.artifacts()).singleElement().satisfies(file -> assertThat(file.content()).contains("[e.name/]"));
    }

    @Test
    @DisplayName("x_transformation_run_and_sync.exp: tipos incompatibles o definiciones no expresables quedan bloqueados con diagnostico y sin ficheros")
    void incompatibleKindsAndNonNativeDefinitionsAreBlockedWithoutFiles(@TempDir Path temporary) throws Exception {
        Env env = Env.open(temporary);

        TransformationExportResult acceleoWithM2m = env.exports().export(new TransformationExportRequest(m2m(false), TransformationExportLanguage.ACCELEO));
        TransformationExportResult atlWithM2t = env.exports().export(new TransformationExportRequest(m2t(), TransformationExportLanguage.ATL));
        TransformationExportResult atlWithGuard = env.exports().export(new TransformationExportRequest(m2m(true), TransformationExportLanguage.ATL));

        assertThat(acceleoWithM2m.compatible()).isFalse();
        assertThat(acceleoWithM2m.diagnostics()).extracting(TransformationExportDiagnostic::code).contains("UNSUPPORTED_KIND");
        assertThat(atlWithM2t.compatible()).isFalse();
        assertThat(atlWithM2t.diagnostics()).extracting(TransformationExportDiagnostic::code).contains("UNSUPPORTED_KIND");
        assertThat(atlWithGuard.compatible()).isFalse();
        assertThat(atlWithGuard.diagnostics()).extracting(TransformationExportDiagnostic::code).contains("GUARD_NOT_NATIVE");
        for(TransformationExportResult blocked : List.of(acceleoWithM2m, atlWithM2t, atlWithGuard)) {
            assertThat(blocked.artifacts()).isEmpty();
            assertThat(blocked.diagnostics()).isNotEmpty().allSatisfy(diagnostic -> assertThat(diagnostic.severity()).isEqualTo("ERROR"));
            assertThat(filesUnder(write(temporary.resolve("out-blocked-" + blocked.language().name().toLowerCase() + blocked.diagnostics().size()), blocked)))
                    .isEmpty();
        }
    }

    @Test
    @DisplayName("x_transformation_run_and_sync.par: resultado esperado -> MATCHED; resultado divergente -> MISMATCHED con diferencias")
    void parityMatchesTheExpectedResultAndDetectsADivergentOne(@TempDir Path temporary) throws Exception {
        Env env = Env.open(temporary);

        TransformationParityReport matched = env.parity().verify(new TransformationParityRequest(m2m(false),
                TransformationExportLanguage.ATL, sourceModel(), expectedTarget("Alice", "Bob"), List.of()));
        TransformationParityReport divergent = env.parity().verify(new TransformationParityRequest(m2m(false),
                TransformationExportLanguage.ATL, sourceModel(), expectedTarget("Alice", "Robert"), List.of()));

        assertThat(matched.status()).isEqualTo(TransformationParityStatus.MATCHED);
        assertThat(matched.compatible()).isTrue();
        assertThat(matched.matched()).isTrue();
        assertThat(matched.differences()).isEmpty();
        assertThat(matched.canonicalFingerprint()).isEqualTo(modelFingerprint(List.of("Alice", "Bob")));
        assertThat(matched.nativeFingerprint()).isEqualTo(modelFingerprint(List.of("Alice", "Bob")));

        assertThat(divergent.status()).isEqualTo(TransformationParityStatus.MISMATCHED);
        assertThat(divergent.matched()).isFalse();
        assertThat(divergent.compatible()).isTrue();
        assertThat(divergent.canonicalFingerprint()).isEqualTo(modelFingerprint(List.of("Alice", "Bob")));
        assertThat(divergent.nativeFingerprint()).isEqualTo(modelFingerprint(List.of("Alice", "Robert")));
        assertThat(divergent.differences()).extracting(TransformationParityDifference::path).allMatch(path -> path.startsWith("$.targetModel."));
        assertThat(divergent.differences()).anySatisfy(difference -> {
            assertThat(difference.path()).contains("3:Bob");
            assertThat(difference.expected()).isEqualTo("1");
            assertThat(difference.actual()).isEqualTo("0");
        });
        assertThat(divergent.differences()).anySatisfy(difference -> {
            assertThat(difference.path()).contains("6:Robert");
            assertThat(difference.expected()).isEqualTo("0");
            assertThat(difference.actual()).isEqualTo("1");
        });
    }

    @Test
    @DisplayName("x_transformation_run_and_sync.par: M2T compara ficheros; exportacion bloqueada o evidencia ausente -> sin veredicto de coincidencia")
    void m2tParityComparesFilesAndBlockedOrMissingEvidenceIsNotAVerdict(@TempDir Path temporary) throws Exception {
        Env env = Env.open(temporary);

        TransformationParityReport textMatched = env.parity().verify(new TransformationParityRequest(m2t(),
                TransformationExportLanguage.JAVA, sourceModel(), null, List.of(
                        new TransformationParityArtifact("out/Alice.txt", "Hello Alice"), new TransformationParityArtifact("out/Bob.txt", "Hello Bob"))));
        TransformationParityReport textDivergent = env.parity().verify(new TransformationParityRequest(m2t(),
                TransformationExportLanguage.JAVA, sourceModel(), null, List.of(
                        new TransformationParityArtifact("out/Alice.txt", "Hello Alice"), new TransformationParityArtifact("out/Bob.txt", "Hello Bobby"))));
        TransformationParityReport blocked = env.parity().verify(new TransformationParityRequest(m2m(true),
                TransformationExportLanguage.ATL, sourceModel(), expectedTarget("Alice", "Bob"), List.of()));
        TransformationParityReport withoutEvidence = env.parity().verify(new TransformationParityRequest(m2m(false),
                TransformationExportLanguage.ATL, sourceModel(), null, List.of()));
        TransformationParityReport noExport = env.parity().verifyExported(new TransformationParityRequest(m2m(false),
                TransformationExportLanguage.ATL, sourceModel(), expectedTarget("Alice", "Bob"), List.of()), null);

        assertThat(textMatched.status()).isEqualTo(TransformationParityStatus.MATCHED);
        assertThat(textMatched.canonicalFingerprint()).isEqualTo(sha256(
                "path=" + scalar("out/Bob.txt") + ";content=" + scalar("Hello Bob") + "\n"
                        + "path=" + scalar("out/Alice.txt") + ";content=" + scalar("Hello Alice")));
        assertThat(textDivergent.status()).isEqualTo(TransformationParityStatus.MISMATCHED);
        assertThat(textDivergent.differences()).isNotEmpty().allSatisfy(difference -> assertThat(difference.path()).startsWith("$.artifacts["));

        assertThat(blocked.status()).isEqualTo(TransformationParityStatus.EXPORT_BLOCKED);
        assertThat(blocked.compatible()).isFalse();
        assertThat(blocked.matched()).isFalse();
        assertThat(blocked.exportDiagnostics()).extracting(TransformationExportDiagnostic::code).contains("GUARD_NOT_NATIVE");
        assertThat(withoutEvidence.status()).isEqualTo(TransformationParityStatus.INVALID_EVIDENCE);
        assertThat(withoutEvidence.differences()).extracting(TransformationParityDifference::path).containsExactly("$.nativeTargetModel");
        assertThat(noExport.status()).isEqualTo(TransformationParityStatus.INVALID_EVIDENCE);
        assertThat(noExport.differences()).extracting(TransformationParityDifference::path).containsExactly("$.export");
    }

    // ----------------------------------------------------------------------- oraculos independientes

    private static String scalar(String value) { return value.length() + ":" + value; }

    private static String sha256(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private static String modelFingerprint(List<String> names) throws Exception {
        List<String> signatures = names.stream()
                .map(name -> "class=" + scalar("class:Person") + ";attributes={" + scalar(NAME) + ":" + scalar(name) + "}").sorted().toList();
        return sha256("metamodel=library\nroots=" + String.join("\n", signatures)
                + "\nelements=" + String.join("\n", signatures) + "\nrelations=");
    }

    /** Escribe los artefactos exportados en un directorio y devuelve el directorio. */
    private static Path write(Path directory, TransformationExportResult result) throws IOException {
        Files.createDirectories(directory);
        for(TransformationExportArtifact artifact : result.artifacts()) {
            Path file = directory.resolve(artifact.path()).normalize();
            if(!file.startsWith(directory)) throw new IllegalStateException("artifact escapes the export directory: " + artifact.path());
            Files.createDirectories(file.getParent());
            Files.writeString(file, artifact.content(), StandardCharsets.UTF_8);
        }
        return directory;
    }

    private static List<Path> filesUnder(Path directory) throws IOException {
        try(Stream<Path> walk = Files.walk(directory)) { return walk.filter(Files::isRegularFile).sorted().toList(); }
    }

    // ----------------------------------------------------------------------------------- fixtures

    private static CanonicalModelDocument sourceModel() {
        CanonicalModelElement alice = new CanonicalModelElement("object:alice", ExpressionTestFixtures.PERSON, Map.of(NAME, "Alice"), Map.of());
        CanonicalModelElement bob = new CanonicalModelElement("object:bob", ExpressionTestFixtures.PERSON, Map.of(NAME, "Bob"), Map.of());
        return new CanonicalModelDocument("1.0", "parity-source", ExpressionTestFixtures.METAMODEL_ID, "", "Parity source",
                List.of("object:alice", "object:bob"), List.of(alice, bob), List.of(), Map.of());
    }

    /** Resultado esperado escrito a mano (ids propios): dos Person raiz con solo el atributo name. */
    private static CanonicalModelDocument expectedTarget(String firstName, String secondName) {
        CanonicalModelElement first = new CanonicalModelElement("expected-1", ExpressionTestFixtures.PERSON, Map.of(NAME, firstName), Map.of());
        CanonicalModelElement second = new CanonicalModelElement("expected-2", ExpressionTestFixtures.PERSON, Map.of(NAME, secondName), Map.of());
        return new CanonicalModelDocument("1.0", "expected-target", ExpressionTestFixtures.METAMODEL_ID, "", "Expected",
                List.of("expected-1", "expected-2"), List.of(first, second), List.of(), Map.of());
    }

    private static TransformationDefinitionDocument m2m(boolean guarded) {
        TransformationBinding binding = new TransformationBinding("binding:name", "person", NAME, TransformationBindingKind.DIRECT,
                NAME, null, null, null, Map.of());
        TransformationRule rule = new TransformationRule("rule:person", "Person to Person", true, "source", ExpressionTestFixtures.PERSON,
                "personSource", guarded ? ExpressionTestFixtures.positiveAge() : null,
                List.of(new TransformationTargetPattern("target:person", "target", ExpressionTestFixtures.PERSON, "person", Map.of())),
                List.of(binding), Map.of());
        return new TransformationDefinitionDocument("1.0", "transformation-export-parity-m2m", "Parity", TransformationKind.M2M,
                List.of(new TransformationEndpoint("source", TransformationEndpointRole.SOURCE, ExpressionTestFixtures.METAMODEL_ID, "", "source", true, Map.of())),
                List.of(new TransformationEndpoint("target", TransformationEndpointRole.TARGET, ExpressionTestFixtures.METAMODEL_ID, "", "target", true, Map.of())),
                List.of(rule), List.of(), null, null, null, Map.of());
    }

    private static TransformationDefinitionDocument m2t() {
        TransformationGenerationRule rule = new TransformationGenerationRule("gen:person", "Generate person", true, "source",
                ExpressionTestFixtures.PERSON, null, "out/{{attr:" + NAME + "}}.txt", "Hello {{attr:" + NAME + "}}", null, Map.of());
        return new TransformationDefinitionDocument("1.0", "transformation-export-parity-m2t", "Parity text", TransformationKind.M2T,
                List.of(new TransformationEndpoint("source", TransformationEndpointRole.SOURCE, ExpressionTestFixtures.METAMODEL_ID, "", "source", true, Map.of())),
                List.of(), List.of(), List.of(rule), null, null, null, Map.of());
    }

    private record Env(TransformationExportService exports, TransformationParityService parity) {
        static Env open(Path temporary) {
            JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();
            StorageProperties storage = new StorageProperties(temporary.resolve("metamodels"), temporary.resolve("models"),
                    temporary.resolve("notations"), temporary.resolve("validations"), temporary.resolve("quickfixes"),
                    temporary.resolve("generators"), temporary.resolve("assets"), temporary.resolve("exports"), true);
            CanonicalMetamodelApplicationService metamodels = new CanonicalMetamodelApplicationService(
                    new JsonCanonicalMetamodelRepository(storage, new CanonicalMetamodelProperties(), mapper), new NeutralMetamodelValidator());
            metamodels.create(ExpressionTestFixtures.metamodel());

            TransformationProperties properties = new TransformationProperties();
            properties.setDirectory(temporary.resolve("transformations"));
            properties.setTestDirectory(temporary.resolve("transformation-tests"));
            properties.setSynchronizationSessionDirectory(temporary.resolve("transformation-sync-sessions"));
            properties.setSynchronizationHistoryDirectory(temporary.resolve("transformation-sync-history"));

            ExpressionTypeChecker typeChecker = new ExpressionTypeChecker(new ExpressionDependencyAnalyzer());
            TransformationDefinitionChecker checker = new TransformationDefinitionChecker(typeChecker);
            HydraArtifactTransactionService transactions = new HydraArtifactTransactionService(
                    temporary.resolve("artifact-transactions").toString(), new HydraFaultInjectionService(new HydraFaultInjectionProperties()));
            TransformationApplicationService transformations = new TransformationApplicationService(
                    new JsonTransformationRepository(properties, mapper), metamodels,
                    new MetamodelValidationFingerprint(mapper), checker,
                    new JsonTransformationTestCaseRepository(properties, mapper, new LocalCanonicalDocumentStoreFactory()),
                    new JsonTransformationSynchronizationSessionRepository(properties, mapper, new LocalCanonicalDocumentStoreFactory()),
                    transactions);
            TransformationRuntimeService runtime = new TransformationRuntimeService(transformations, metamodels,
                    new TransformationPlanCompiler(checker), new CanonicalTransformationRuntime(new ExpressionEvaluator(typeChecker)));
            TransformationTextGenerationService text = new TransformationTextGenerationService(transformations, metamodels, checker,
                    new CanonicalTextGenerationRuntime(new ExpressionEvaluator(typeChecker)));
            TransformationLanguageAdapterRegistry registry = new TransformationLanguageAdapterRegistry(List.of(
                    new AtlTransformationLanguageAdapter(), new EtlTransformationLanguageAdapter(),
                    new JavaTransformationLanguageAdapter(PLAIN), new XtendTransformationLanguageAdapter(PLAIN),
                    new PythonTransformationLanguageAdapter(PLAIN), new GoTransformationLanguageAdapter(PLAIN),
                    new JavaScriptTransformationLanguageAdapter(PLAIN), new TypeScriptTransformationLanguageAdapter(PLAIN),
                    new AcceleoTransformationLanguageAdapter(), new EglTransformationLanguageAdapter()));
            TransformationExportService exports = new TransformationExportService(registry, checker, metamodels);
            return new Env(exports, new TransformationParityService(exports, runtime, text, new TransformationParityComparator()));
        }
    }
}
