package com.example.editor.grammar.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.editor.grammar.GrammarProperties;
import com.example.editor.grammar.core.CompilationStatus;
import com.example.editor.grammar.core.GeneratedArtifactRef;
import com.example.editor.grammar.core.GrammarAuthoringMode;
import com.example.editor.grammar.core.GrammarCompilationRequest;
import com.example.editor.grammar.core.GrammarDefinitionModel;
import com.example.editor.grammar.core.GrammarSemanticBinding;
import com.example.editor.grammar.core.GrammarSemanticBindingMap;
import com.example.editor.grammar.core.GrammarSeverity;
import com.example.editor.grammar.core.GrammarSourceDocument;
import com.example.editor.grammar.core.GrammarTechnology;
import com.example.editor.grammar.persistence.JsonGrammarActivationRepository;
import com.example.editor.grammar.persistence.JsonGrammarCompilationRepository;
import com.example.editor.grammar.persistence.JsonGrammarRepository;
import com.example.editor.grammar.provider.GrammarProviderRegistry;
import com.example.editor.grammar.provider.antlr.AntlrGrammarProvider;
import com.example.editor.grammar.provider.langium.LangiumGrammarProvider;
import com.example.editor.grammar.provider.xtext.XtextGrammarProvider;
import com.example.editor.grammar.runtime.GrammarCompilationSnapshot;
import com.example.editor.grammar.runtime.GrammarCompilationStage;
import com.example.editor.grammar.runtime.GrammarCompilationStageResult;
import com.example.editor.grammar.visualization.GrammarClassDiagramProjectionService;
import com.example.editor.integration.verification.ExternalToolCircuitBreaker;
import com.example.editor.integration.verification.ExternalToolProcessRunner;
import com.example.editor.persistence.LocalCanonicalDocumentStoreFactory;
import com.example.editor.security.process.HydraExternalProcessPolicy;
import com.example.editor.security.process.HydraExternalProcessSecurityProperties;
import com.example.editor.security.process.HydraTemporaryWorkspace;
import tools.jackson.databind.json.JsonMapper;

/**
 * Resultados de las etapas de la tuberia de compilacion de gramaticas sobre cableado real (repositorios JSON en
 * directorio temporal, proveedores y analizador reales; sin herramientas nativas). Etapas certificadas:
 * x_grammar_to_text_model.mm (metamodelo obligatorio solo en modo enlazado y primero el metamodelo),
 * x_grammar_to_text_model.d1 (un error bloqueante impide generar y activar), grammar_compile_pipeline.s1 (congelar
 * borrador y huella), .s5 (contrato semantico y capas) y .s11 (artefactos registrados con huella y tamano).
 * Las huellas se recalculan en el propio test con SHA-256; los literales esperados no se piden al codigo probado.
 */
class GrammarAuthoringCompilePipelineOutcomeTests {

    // Native compilation needs a Langium CLI that runs on the HYDRA Node runtime: 4.3 fails langium generate on Node >= 24.20.
    private static final String PROFILE = "langium-4.4-browser";
    // langium generate rejects rules that reference undeclared terminals, so the fixture declares them like the designer's default grammar.
    private static final String STABLE = "grammar Stable\nentry Model: nodes+=Node*;\nNode: 'node' name=ID;\nhidden terminal WS: /\\s+/;\nterminal ID: /[_a-zA-Z][\\w_]*/;\n";

    private GrammarProperties properties;
    private GrammarApplicationService grammars;
    private JsonGrammarCompilationRepository compilations;
    private GrammarCompilationService service;
    private Path root;

    private void wire(Path temporary) {
        root = temporary;
        properties = new GrammarProperties();
        properties.setDirectory(temporary.resolve("grammars"));
        properties.setCompilationDirectory(temporary.resolve("grammar-compilations"));
        properties.setActivationDirectory(temporary.resolve("grammar-activations"));
        JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();
        LocalCanonicalDocumentStoreFactory stores = new LocalCanonicalDocumentStoreFactory();

        GrammarToolchainProfileService profiles = new GrammarToolchainProfileService();
        GrammarProviderRegistry providers = new GrammarProviderRegistry(List.of(
                new AntlrGrammarProvider(properties), new LangiumGrammarProvider(properties), new XtextGrammarProvider(properties)));
        grammars = new GrammarApplicationService(new JsonGrammarRepository(properties, mapper, stores), properties, profiles, providers);
        GrammarAnalysisCoordinator analysis = new GrammarAnalysisCoordinator(profiles, providers,
                new GrammarAnalysisCache(properties), new com.example.editor.grammar.runtime.GrammarProviderHealthService(properties), properties);
        compilations = new JsonGrammarCompilationRepository(properties, mapper, stores);
        JsonGrammarActivationRepository activations = new JsonGrammarActivationRepository(properties, mapper, stores);
        GrammarNativeToolchainService nativeToolchain = new GrammarNativeToolchainService(
                new ExternalToolProcessRunner(new ExternalToolCircuitBreaker()),
                new HydraTemporaryWorkspace(new HydraExternalProcessPolicy(new HydraExternalProcessSecurityProperties())),
                properties);
        service = new GrammarCompilationService(grammars, profiles, providers, analysis, compilations, activations, nativeToolchain,
                new GrammarClassDiagramProjectionService(properties), properties, new GrammarCompilationProgressService(properties));
    }

    private static GrammarSourceDocument document(String text) {
        return new GrammarSourceDocument("main", "main.langium", "langium", text, "", true, false);
    }

    private static GrammarDefinitionModel grammar(String id, GrammarAuthoringMode mode, String metamodelId, GrammarSourceDocument document) {
        return new GrammarDefinitionModel("1.0", id, id, GrammarTechnology.LANGIUM, mode, metamodelId, PROFILE, "main", "Model",
                List.of(document), GrammarSemanticBindingMap.empty(), "", Map.of(), Map.of());
    }

    // ---- oraculos independientes (SHA-256 recalculado en el test) ----

    private static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String sha256(String value) {
        return HexFormat.of().formatHex(digest().digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private static void put(MessageDigest digest, String value) {
        digest.update(value.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
    }

    /** Huella del conjunto de documentos: cada campo de texto seguido de un cero y dos bytes para las dos marcas. */
    private static String sourcesHash(List<GrammarSourceDocument> documents) {
        MessageDigest digest = digest();
        documents.stream().sorted(Comparator.comparing(GrammarSourceDocument::relativePath).thenComparing(GrammarSourceDocument::id)).forEach(item -> {
            put(digest, item.id());
            put(digest, item.relativePath());
            put(digest, item.languageId());
            put(digest, item.content());
            digest.update((byte) (item.entryPoint() ? 1 : 0));
            digest.update((byte) (item.generated() ? 1 : 0));
        });
        return HexFormat.of().formatHex(digest.digest());
    }

    /** Etag del contrato semantico: huella del id, un cero y las correspondencias ordenadas campo a campo. */
    private static String contractEtag(String contractId, GrammarSemanticBindingMap bindings) {
        StringBuilder fingerprint = new StringBuilder();
        bindings.bindings().stream()
                .sorted(Comparator.comparing(GrammarSemanticBinding::grammarElementId).thenComparing(GrammarSemanticBinding::semanticElementId))
                .forEach(binding -> fingerprint.append(binding.grammarElementId()).append('\0').append(binding.grammarElementKind()).append('\0')
                        .append(binding.semanticElementId()).append('\0').append(binding.semanticElementKind()).append('\0')
                        .append(binding.confidence()).append('\n'));
        return sha256(contractId + "\0" + fingerprint);
    }

    private static long countFiles(Path directory) throws IOException {
        if (!Files.exists(directory)) return 0;
        try (Stream<Path> files = Files.walk(directory)) {
            return files.filter(Files::isRegularFile).count();
        }
    }

    private static GrammarCompilationStageResult stage(GrammarCompilationSnapshot snapshot, GrammarCompilationStage stage) {
        return snapshot.stages().stream().filter(item -> item.stage() == stage).findFirst().orElseThrow();
    }

    // ---- x_grammar_to_text_model.mm ----

    @Test
    @DisplayName("x_grammar_to_text_model.mm: el metamodelo es obligatorio solo en los modos enlazado y primero el metamodelo, y queda guardado en disco")
    void theMetamodelIsRequiredOnlyInLinkedAndMetamodelFirstModesAndIsPersisted(@TempDir Path temporary) throws Exception {
        wire(temporary);
        GrammarSourceDocument source = document(STABLE);

        grammars.create(grammar("mm-linked", GrammarAuthoringMode.LINKED, "people", source));
        grammars.create(grammar("mm-first", GrammarAuthoringMode.METAMODEL_FIRST, "people", source));
        grammars.create(grammar("mm-free", GrammarAuthoringMode.GRAMMAR_FIRST, "", source));

        GrammarDefinitionModel linked = grammars.find("mm-linked").grammar();
        assertThat(linked.authoringMode()).isEqualTo(GrammarAuthoringMode.LINKED);
        assertThat(linked.metamodelId()).isEqualTo("people");
        assertThat(grammars.find("mm-first").grammar().metamodelId()).isEqualTo("people");
        assertThat(grammars.find("mm-free").grammar().metamodelId()).isEmpty();

        // Oraculo en disco: el fichero guardado de la gramatica enlazada contiene el modo y el metamodelo literales.
        List<String> stored;
        try (Stream<Path> files = Files.walk(properties.getDirectory())) {
            stored = files.filter(Files::isRegularFile).map(path -> {
                try {
                    return Files.readString(path, StandardCharsets.UTF_8).replaceAll("\\s", "");
                } catch (IOException exception) {
                    throw new IllegalStateException(exception);
                }
            }).filter(text -> text.contains("\"id\":\"mm-linked\"")).collect(Collectors.toList());
        }
        assertThat(stored).hasSize(1);
        assertThat(stored.get(0)).contains("\"authoringMode\":\"LINKED\"").contains("\"metamodelId\":\"people\"");

        // Rechazo: sin metamodelo, esos dos modos no se guardan y no queda ningun fichero nuevo.
        long filesBefore = countFiles(properties.getDirectory());
        int listedBefore = grammars.list().size();
        for (GrammarAuthoringMode mode : List.of(GrammarAuthoringMode.LINKED, GrammarAuthoringMode.METAMODEL_FIRST)) {
            GrammarDefinitionModel withoutMetamodel = grammar("mm-missing-" + mode.name().toLowerCase(), mode, "", source);
            assertThatThrownBy(() -> grammars.create(withoutMetamodel)).isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("requires a metamodelId");
        }
        assertThat(countFiles(properties.getDirectory())).isEqualTo(filesBefore);
        assertThat(grammars.list()).hasSize(listedBefore);
    }

    // ---- grammar_compile_pipeline.s1 ----

    @Test
    @DisplayName("grammar_compile_pipeline.s1: congelar el borrador registra la huella recalculada y un texto distinto del esperado se rechaza sin dejar rastro")
    void freezingRecordsTheRecomputedHashAndRejectsChangedText(@TempDir Path temporary) throws Exception {
        wire(temporary);
        GrammarSourceDocument source = document(STABLE);
        grammars.create(grammar("freeze", GrammarAuthoringMode.GRAMMAR_FIRST, "", source));
        String expected = sourcesHash(List.of(source));
        assertThat(expected).hasSize(64);

        GrammarCompilationSnapshot snapshot = service.compile("freeze",
                new GrammarCompilationRequest("req-freeze", "freeze", 1, List.of(source), PROFILE, expected, false, Map.of()));

        assertThat(snapshot.successful()).isTrue();
        assertThat(snapshot.sourceHash()).isEqualTo(expected);
        GrammarCompilationStageResult freeze = snapshot.stages().get(0);
        assertThat(freeze.stage()).isEqualTo(GrammarCompilationStage.FREEZE_DRAFT);
        assertThat(freeze.successful()).isTrue();
        assertThat(freeze.metadata()).containsEntry("sourceHash", expected).containsEntry("documents", "1");

        // El texto cambio despues de calcular la huella esperada: la peticion se rechaza ya al construirla...
        GrammarSourceDocument changed = document(STABLE + "Other: 'other' name=ID;\n");
        assertThatThrownBy(() -> new GrammarCompilationRequest("req-stale", "freeze", 2, List.of(changed), PROFILE, expected, false, Map.of()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("expectedSourceHash does not match");

        // ... y, sin documentos en la peticion, el servicio compara contra el texto guardado y tambien lo rechaza.
        grammars.replace("freeze", grammar("freeze", GrammarAuthoringMode.GRAMMAR_FIRST, "", changed), grammars.find("freeze").etag());
        GrammarCompilationRequest againstStored = new GrammarCompilationRequest("req-stored", "freeze", 3, List.of(), PROFILE, expected, false, Map.of());
        List<String> snapshotsBefore = compilations.listIds();
        long filesBefore = countFiles(root.resolve("grammar-compilations"));
        assertThatThrownBy(() -> service.compile("freeze", againstStored)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expectedSourceHash does not match");
        assertThat(compilations.listIds()).isEqualTo(snapshotsBefore);
        assertThat(countFiles(root.resolve("grammar-compilations"))).isEqualTo(filesBefore);
        assertThat(service.active("freeze")).isEmpty();
    }

    // ---- grammar_compile_pipeline.s5 ----

    @Test
    @DisplayName("grammar_compile_pipeline.s5: el contrato semantico fija su id y su huella y la comprobacion de capas distingue gramatica libre y enlazada")
    void theSemanticContractIdAndEtagAreFixedAndTheLayerCheckReportsItsMode(@TempDir Path temporary) {
        wire(temporary);
        GrammarSourceDocument source = document(STABLE);
        String hash = sourcesHash(List.of(source));
        grammars.create(grammar("contract-free", GrammarAuthoringMode.GRAMMAR_FIRST, "", source));
        grammars.create(grammar("contract-linked", GrammarAuthoringMode.LINKED, "people", source));

        GrammarCompilationSnapshot free = service.compile("contract-free",
                new GrammarCompilationRequest("contract-free", 1, List.of(source), PROFILE, false));
        assertThat(free.successful()).isTrue();
        assertThat(free.semanticContractId()).isEqualTo("grammar.contract-free." + hash.substring(0, 12));
        assertThat(free.semanticContractEtag()).isEqualTo(contractEtag(free.semanticContractId(), free.semanticBindings()));
        assertThat(stage(free, GrammarCompilationStage.RECONCILE_SEMANTIC_CONTRACT).metadata())
                .containsEntry("semanticContractId", free.semanticContractId());
        assertThat(stage(free, GrammarCompilationStage.CHECK_HYDRA_LAYERS).metadata())
                .containsEntry("mode", "SEMANTIC_CONTRACT_ONLY").containsEntry("metamodelId", "");
        assertThat(free.semanticBindings().bindings()).extracting(GrammarSemanticBinding::semanticElementId).containsExactlyInAnyOrder("Model", "Node");

        GrammarCompilationSnapshot linked = service.compile("contract-linked",
                new GrammarCompilationRequest("contract-linked", 1, List.of(source), PROFILE, false));
        assertThat(linked.successful()).isTrue();
        assertThat(linked.semanticContractId()).isEqualTo("people");
        assertThat(linked.semanticContractEtag()).isEqualTo(contractEtag("people", linked.semanticBindings()));
        assertThat(linked.semanticContractEtag()).isNotEqualTo(free.semanticContractEtag());
        assertThat(stage(linked, GrammarCompilationStage.CHECK_HYDRA_LAYERS).metadata())
                .containsEntry("mode", "METAMODEL_LINKED").containsEntry("metamodelId", "people");

        // La huella y el id sobreviven a la persistencia: se releen del repositorio real.
        GrammarCompilationSnapshot reread = compilations.findById(linked.id()).orElseThrow().document();
        assertThat(reread.semanticContractId()).isEqualTo("people");
        assertThat(reread.semanticContractEtag()).isEqualTo(linked.semanticContractEtag());
    }

    // ---- grammar_compile_pipeline.s11 ----

    @Test
    @DisplayName("grammar_compile_pipeline.s11: cada fichero generado queda registrado con su ruta, su huella SHA-256 recalculada y su tamano en bytes")
    void everyGeneratedFileIsRegisteredWithItsRecomputedHashAndSize(@TempDir Path temporary) {
        wire(temporary);
        GrammarSourceDocument source = document(STABLE);
        grammars.create(grammar("artifacts", GrammarAuthoringMode.GRAMMAR_FIRST, "", source));

        GrammarCompilationSnapshot snapshot = service.compile("artifacts",
                new GrammarCompilationRequest("artifacts", 1, List.of(source), PROFILE, false));

        assertThat(snapshot.successful()).isTrue();
        Map<String, GeneratedArtifactRef> byPath = snapshot.artifacts().stream()
                .collect(Collectors.toMap(GeneratedArtifactRef::path, Function.identity()));
        assertThat(byPath).hasSize(snapshot.artifacts().size());
        assertThat(snapshot.generatedProject().files()).isNotEmpty();
        snapshot.generatedProject().files().forEach((path, content) -> {
            GeneratedArtifactRef ref = byPath.get(path);
            assertThat(ref).as("registro de " + path).isNotNull();
            assertThat(ref.sha256()).as("huella de " + path).isEqualTo(sha256(content));
            assertThat(ref.sizeBytes()).as("tamano de " + path).isEqualTo(content.getBytes(StandardCharsets.UTF_8).length);
            assertThat(snapshot.checksums()).containsEntry(path, ref.sha256());
        });
        snapshot.runtimeFiles().forEach((path, content) -> {
            GeneratedArtifactRef ref = byPath.get("runtime/" + path);
            assertThat(ref).as("registro de runtime/" + path).isNotNull();
            assertThat(ref.kind()).isEqualTo("RUNTIME_FILE");
            assertThat(ref.sha256()).isEqualTo(sha256(content));
            assertThat(ref.sizeBytes()).isEqualTo(content.getBytes(StandardCharsets.UTF_8).length);
        });
        assertThat(snapshot.artifacts().size()).isGreaterThanOrEqualTo(snapshot.generatedProject().files().size() + snapshot.runtimeFiles().size());

        // La gramatica fuente conservada en el proyecto es literalmente el texto del borrador congelado.
        GeneratedArtifactRef grammarRef = byPath.get("main.langium");
        assertThat(grammarRef).isNotNull();
        assertThat(grammarRef.kind()).isEqualTo("GRAMMAR_SOURCE");
        assertThat(grammarRef.sha256()).isEqualTo(sha256(STABLE));
        assertThat(grammarRef.sizeBytes()).isEqualTo(STABLE.getBytes(StandardCharsets.UTF_8).length);

        assertThat(stage(snapshot, GrammarCompilationStage.REGISTER_ARTIFACTS).metadata())
                .containsEntry("artifacts", Integer.toString(snapshot.artifacts().size()));

        GrammarCompilationSnapshot reread = compilations.findById(snapshot.id()).orElseThrow().document();
        assertThat(reread.artifacts()).isEqualTo(snapshot.artifacts());
    }

    // ---- x_grammar_to_text_model.d1 ----

    @Test
    @DisplayName("x_grammar_to_text_model.d1 / grammar_author_save_compile.bl: con un error bloqueante ni compilar ni generar producen ficheros ni cambian la version activa")
    void aBlockingErrorStopsBothCompileAndGenerateBeforeAnyFileIsProduced(@TempDir Path temporary) {
        wire(temporary);
        GrammarSourceDocument good = document(STABLE);
        grammars.create(grammar("gate", GrammarAuthoringMode.GRAMMAR_FIRST, "", good));
        GrammarCompilationSnapshot first = service.compile("gate", new GrammarCompilationRequest("gate-ok", 1, List.of(good), PROFILE, true));
        assertThat(first.successful()).isTrue();

        GrammarSourceDocument broken = document("!!! this is not a valid langium grammar !!!");
        Set<GrammarCompilationStage> afterAnalysis = Set.of(GrammarCompilationStage.GENERATE_PROJECT,
                GrammarCompilationStage.GENERATE_SEMANTIC_ARTIFACTS, GrammarCompilationStage.MATERIALIZE_RUNTIME,
                GrammarCompilationStage.SMOKE_TEST, GrammarCompilationStage.REGISTER_ARTIFACTS, GrammarCompilationStage.PROMOTE_SNAPSHOT);

        long revision = 2;
        for (boolean activate : new boolean[] { true, false }) {
            GrammarCompilationSnapshot failed = service.compile("gate",
                    new GrammarCompilationRequest("gate-broken-" + activate, "gate", revision++, List.of(broken), PROFILE, "", activate, Map.of()));

            assertThat(failed.status()).isEqualTo(CompilationStatus.FAILED);
            assertThat(failed.diagnostics()).anyMatch(item -> item.severity() == GrammarSeverity.ERROR);
            List<GrammarCompilationStageResult> notSuccessful = failed.stages().stream().filter(item -> !item.successful()).collect(Collectors.toList());
            assertThat(notSuccessful).hasSize(1);
            assertThat(notSuccessful.get(0).stage()).isIn(GrammarCompilationStage.FULL_ANALYSIS, GrammarCompilationStage.RESOLVE_IMPORTS);
            assertThat(failed.stages()).extracting(GrammarCompilationStageResult::stage).doesNotContainAnyElementsOf(afterAnalysis);
            assertThat(failed.artifacts()).isEmpty();
            assertThat(failed.generatedProject().files()).isEmpty();
            assertThat(failed.runtimeFiles()).isEmpty();
            assertThat(compilations.findById(failed.id())).isPresent();
            assertThat(service.active("gate")).isPresent().get().extracting(GrammarCompilationSnapshot::id).isEqualTo(first.id());
        }
    }
}
