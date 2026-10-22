package com.example.editor.transformation.textmodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import com.example.editor.grammar.GrammarProperties;
import com.example.editor.grammar.application.GrammarAnalysisCache;
import com.example.editor.grammar.application.GrammarAnalysisCoordinator;
import com.example.editor.grammar.application.GrammarApplicationService;
import com.example.editor.grammar.application.GrammarCompilationProgressService;
import com.example.editor.grammar.application.GrammarCompilationService;
import com.example.editor.grammar.application.GrammarNativeToolchainService;
import com.example.editor.grammar.application.GrammarToolchainProfileService;
import com.example.editor.grammar.core.CompilationStatus;
import com.example.editor.grammar.core.GrammarAuthoringMode;
import com.example.editor.grammar.core.GrammarCompilationRequest;
import com.example.editor.grammar.core.GrammarDefinitionModel;
import com.example.editor.grammar.core.GrammarSemanticBindingMap;
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
import com.example.editor.grammar.runtime.LastKnownGoodGrammarService;
import com.example.editor.grammar.visualization.GrammarClassDiagramProjectionService;
import com.example.editor.integration.verification.ExternalToolCircuitBreaker;
import com.example.editor.integration.verification.ExternalToolProcessRunner;
import com.example.editor.metamodel.application.CanonicalMetamodelApplicationService;
import com.example.editor.metamodel.canonical.CanonicalMetamodelProperties;
import com.example.editor.metamodel.canonical.JsonCanonicalMetamodelRepository;
import com.example.editor.metamodel.core.MetaAttribute;
import com.example.editor.metamodel.core.MetaClass;
import com.example.editor.metamodel.core.MetaClassKind;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.core.MetaPrimitiveType;
import com.example.editor.metamodel.core.MetaTypeReference;
import com.example.editor.metamodel.core.Multiplicity;
import com.example.editor.metamodel.validation.MetamodelValidationFingerprint;
import com.example.editor.metamodel.validation.NeutralMetamodelValidator;
import com.example.editor.persistence.LocalCanonicalDocumentStoreFactory;
import com.example.editor.security.process.HydraExternalProcessPolicy;
import com.example.editor.security.process.HydraExternalProcessSecurityProperties;
import com.example.editor.security.process.HydraTemporaryWorkspace;
import com.example.editor.semantic.expression.ExpressionDependencyAnalyzer;
import com.example.editor.semantic.expression.ExpressionEvaluator;
import com.example.editor.semantic.expression.ExpressionTypeChecker;
import com.example.editor.testing.fault.HydraFaultInjectionProperties;
import com.example.editor.testing.fault.HydraFaultInjectionService;
import com.example.editor.transformation.TransformationApplicationException;
import com.example.editor.transformation.TransformationApplicationService;
import com.example.editor.transformation.TransformationDefinitionChecker;
import com.example.editor.transformation.TransformationProperties;
import com.example.editor.transformation.core.TransformationBinding;
import com.example.editor.transformation.core.TransformationBindingKind;
import com.example.editor.transformation.core.TransformationDefinitionDocument;
import com.example.editor.transformation.core.TransformationEndpoint;
import com.example.editor.transformation.core.TransformationEndpointRole;
import com.example.editor.transformation.core.TransformationKind;
import com.example.editor.transformation.core.TransformationRule;
import com.example.editor.transformation.core.TransformationTargetPattern;
import com.example.editor.transformation.core.TransformationTextToModelConfiguration;
import com.example.editor.transformation.persistence.JsonTransformationRepository;
import com.example.editor.transformation.runtime.CanonicalTransformationRuntime;
import com.example.editor.transformation.runtime.TransformationPlanCompiler;
import com.example.editor.transformation.runtime.TransformationRuntimeService;
import com.example.editor.transformation.sync.JsonTransformationSynchronizationSessionRepository;
import com.example.editor.transformation.testing.persistence.JsonTransformationTestCaseRepository;
import com.example.editor.validation.model.CanonicalModelElement;
import com.jayway.jsonpath.JsonPath;
import tools.jackson.databind.json.JsonMapper;

/**
 * Evidencia real, sin mocks y sin herramientas nativas, del texto a modelo con definiciones GUARDADAS:
 * transformation_author_text2model.gr / .pt / .po y x_grammar_to_text_model.t2m. Gramaticas Langium reales
 * compiladas y activadas, repositorios JSON reales en @TempDir y los servicios de transformacion reales. La
 * definicion guardada elige la gramatica y la ruta logica del texto, y una transformacion de modelo a modelo
 * guardada proyecta despues el modelo leido. Los oraculos son los nombres del texto escrito en la prueba, los
 * valores literales del resultado, el JSON de la definicion leido del disco y una huella del texto calculada aqui.
 */
class TransformationTextToModelSavedDefinitionPostProjectionIntegrationTests {
    private static final String NODE_GRAMMAR = "grammar Stable\nentry Model: nodes+=Node*;\nNode: 'node' name=ID;\n";
    private static final String ITEM_GRAMMAR = "grammar Other\nentry Model: items+=Item*;\nItem: 'item' name=ID;\n";
    private static final String ENTRY = "class:Entry";
    private static final String LABEL = "attribute:Entry:label";
    private static final String TARGET_METAMODEL = "t2m-target";
    private static final String LOGICAL_PATH = "models/people.hydra";

    private GrammarApplicationService grammars;
    private GrammarCompilationService compilations;
    private CanonicalMetamodelApplicationService metamodels;
    private TransformationApplicationService transformations;
    private TransformationTextToModelService t2m;

    private void wire(Path temporary) {
        GrammarProperties properties = new GrammarProperties();
        properties.setDirectory(temporary.resolve("grammars"));
        properties.setCompilationDirectory(temporary.resolve("grammar-compilations"));
        properties.setActivationDirectory(temporary.resolve("grammar-activations"));
        JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();
        LocalCanonicalDocumentStoreFactory stores = new LocalCanonicalDocumentStoreFactory();
        GrammarToolchainProfileService profiles = new GrammarToolchainProfileService();
        GrammarProviderRegistry providers = new GrammarProviderRegistry(List.of(
                new AntlrGrammarProvider(properties), new LangiumGrammarProvider(properties), new XtextGrammarProvider(properties)));
        grammars = new GrammarApplicationService(new JsonGrammarRepository(properties, mapper, stores), properties, profiles, providers);
        GrammarAnalysisCoordinator analysis = new GrammarAnalysisCoordinator(profiles, providers, new GrammarAnalysisCache(properties),
                new com.example.editor.grammar.runtime.GrammarProviderHealthService(properties), properties);
        GrammarNativeToolchainService nativeToolchain = new GrammarNativeToolchainService(
                new ExternalToolProcessRunner(new ExternalToolCircuitBreaker()),
                new HydraTemporaryWorkspace(new HydraExternalProcessPolicy(new HydraExternalProcessSecurityProperties())), properties);
        compilations = new GrammarCompilationService(grammars, profiles, providers, analysis,
                new JsonGrammarCompilationRepository(properties, mapper, stores), new JsonGrammarActivationRepository(properties, mapper, stores),
                nativeToolchain, new GrammarClassDiagramProjectionService(properties), properties, new GrammarCompilationProgressService(properties));

        StorageProperties storage = new StorageProperties(temporary.resolve("metamodels"), temporary.resolve("models"),
                temporary.resolve("notations"), temporary.resolve("validations"), temporary.resolve("quickfixes"),
                temporary.resolve("generators"), temporary.resolve("assets"), temporary.resolve("exports"), true);
        metamodels = new CanonicalMetamodelApplicationService(
                new JsonCanonicalMetamodelRepository(storage, new CanonicalMetamodelProperties(), mapper), new NeutralMetamodelValidator());
        TransformationProperties transformationProperties = new TransformationProperties();
        transformationProperties.setDirectory(temporary.resolve("transformations"));
        transformationProperties.setTestDirectory(temporary.resolve("transformation-tests"));
        transformationProperties.setSynchronizationSessionDirectory(temporary.resolve("transformation-sync-sessions"));
        transformationProperties.setSynchronizationHistoryDirectory(temporary.resolve("transformation-sync-history"));
        ExpressionTypeChecker typeChecker = new ExpressionTypeChecker(new ExpressionDependencyAnalyzer());
        TransformationDefinitionChecker checker = new TransformationDefinitionChecker(typeChecker);
        HydraArtifactTransactionService transactions = new HydraArtifactTransactionService(
                temporary.resolve("artifact-transactions").toString(), new HydraFaultInjectionService(new HydraFaultInjectionProperties()));
        transformations = new TransformationApplicationService(
                new JsonTransformationRepository(transformationProperties, mapper), metamodels,
                new MetamodelValidationFingerprint(mapper), checker,
                new JsonTransformationTestCaseRepository(transformationProperties, mapper, stores),
                new JsonTransformationSynchronizationSessionRepository(transformationProperties, mapper, stores), transactions);
        TransformationRuntimeService runtime = new TransformationRuntimeService(transformations, metamodels,
                new TransformationPlanCompiler(checker), new CanonicalTransformationRuntime(new ExpressionEvaluator(typeChecker)));
        t2m = new TransformationTextToModelService(transformations, checker,
                new GrammarTransformationTextParserGateway(new LastKnownGoodGrammarService(compilations)), runtime);
    }

    private GrammarCompilationSnapshot createAndCompile(String id, String source, boolean activate) {
        GrammarSourceDocument document = new GrammarSourceDocument("main", "main.langium", "langium", source, "", true, false);
        grammars.create(new GrammarDefinitionModel("1.0", id, id, GrammarTechnology.LANGIUM, GrammarAuthoringMode.GRAMMAR_FIRST, "",
                "langium-4.3-browser", "main", "Model", List.of(document), GrammarSemanticBindingMap.empty(), "", Map.of(), Map.of()));
        GrammarCompilationSnapshot snapshot = compilations.compile(id, new GrammarCompilationRequest(id, 1, List.of(document), "langium-4.3-browser", activate));
        assertThat(snapshot.status()).isEqualTo(CompilationStatus.SUCCESS);
        return snapshot;
    }

    private static TransformationDefinitionDocument t2mDocument(String id, String grammarId, String path, String postTransformationId) {
        return new TransformationDefinitionDocument("1.1", id, "Text to model " + id, TransformationKind.T2M,
                List.of(), List.of(), List.of(), List.of(), new TransformationTextToModelConfiguration(grammarId, path, postTransformationId), null, null, Map.of());
    }

    /** Registra el metamodelo que publica la gramatica (id y clase tal como los lee el analizador) y el metamodelo destino, y guarda la proyeccion. */
    private void saveProjection(String transformationId, String sourceMetamodelId, String sourceClassId) {
        MetaClass node = new MetaClass(sourceClassId, "Node", MetaClassKind.CLASS, List.of(), List.of(), Map.of());
        metamodels.create(new MetaModel(MetaModel.CURRENT_SCHEMA_VERSION, sourceMetamodelId, "Grammar semantic model", List.of(node),
                List.of(), List.of(), List.of(), Map.of()));
        MetaAttribute label = new MetaAttribute(LABEL, "label", MetaTypeReference.primitive(MetaPrimitiveType.STRING),
                Multiplicity.optional(), true, true, "", Map.of());
        MetaClass entry = new MetaClass(ENTRY, "Entry", MetaClassKind.CLASS, List.of(label), List.of(), Map.of());
        metamodels.create(new MetaModel(MetaModel.CURRENT_SCHEMA_VERSION, TARGET_METAMODEL, "Entries", List.of(entry),
                List.of(), List.of(), List.of(), Map.of()));
        TransformationRule rule = new TransformationRule("rule:node-to-entry", "Node to Entry", true, "source", sourceClassId, "nodeSource", null,
                List.of(new TransformationTargetPattern("target:entry", "target", ENTRY, "entry", Map.of())),
                List.of(new TransformationBinding("binding:label", "entry", LABEL, TransformationBindingKind.CONSTANT, "", "projected", null, null, Map.of())),
                Map.of());
        transformations.create(new TransformationDefinitionDocument("1.0", transformationId, "Project nodes", TransformationKind.M2M,
                List.of(new TransformationEndpoint("source", TransformationEndpointRole.SOURCE, sourceMetamodelId, "", "source", true, Map.of())),
                List.of(new TransformationEndpoint("target", TransformationEndpointRole.TARGET, TARGET_METAMODEL, "", "target", true, Map.of())),
                List.of(rule), List.of(), null, null, null, Map.of()));
    }

    private static String sha256(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private static List<Path> filesNamed(Path directory, String fragment) throws IOException {
        try (Stream<Path> walk = Files.walk(directory)) {
            return walk.filter(Files::isRegularFile).filter(path -> path.getFileName().toString().contains(fragment)).toList();
        }
    }

    @Test
    @DisplayName("transformation_author_text2model.gr / .pt / .po + x_grammar_to_text_model.t2m: la definicion guardada lee el texto con su gramatica y su ruta y el modelo final es el proyectado por la transformacion posterior")
    void aSavedDefinitionReadsTheTextAndProjectsItThroughThePostTransformation(@TempDir Path temporary) throws Exception {
        wire(temporary);
        GrammarCompilationSnapshot nodes = createAndCompile("t2m-saved-nodes", NODE_GRAMMAR, true);
        GrammarCompilationSnapshot items = createAndCompile("t2m-saved-items", ITEM_GRAMMAR, true);
        String text = "node alpha\nnode beta\n";

        // Lectura previa sin proyeccion: de aqui se toma el metamodelo y la clase que la gramatica publica para el modelo leido.
        var plain = t2m.preview(t2mDocument("t2m-unsaved-probe", "t2m-saved-nodes", "model.hydra", ""), text);
        assertThat(plain.parsedModel()).isNotNull();
        saveProjection("transformation-t2m-post", plain.parsedModel().metamodelId(), plain.parsedModel().elements().getFirst().classId());
        TransformationDefinitionDocument saved = t2mDocument("t2m-saved-definition", "t2m-saved-nodes", LOGICAL_PATH, "transformation-t2m-post");
        transformations.create(saved);

        var result = t2m.execute("t2m-saved-definition", text);

        // gr: se uso el snapshot activo de la gramatica elegida y el texto se leyo sin diagnosticos.
        assertThat(result.diagnostics()).isEmpty();
        assertThat(result.grammarSnapshotId()).isEqualTo(nodes.id()).isNotEqualTo(items.id());
        assertThat(result.parsedModel().elements()).extracting(CanonicalModelElement::id).containsExactly("alpha", "beta");
        // pt: la ruta logica de la definicion guardada llega al lector (la huella del texto la incluye).
        assertThat(result.parsedModel().annotations().get("sourceHash")).isEqualTo(sha256("transformation-input\0" + LOGICAL_PATH + "\0" + text + "\0"));
        List<Path> stored = filesNamed(temporary.resolve("transformations"), "t2m-saved-definition");
        assertThat(stored).isNotEmpty();
        String disk = Files.readString(stored.stream().filter(path -> path.getFileName().toString().endsWith(".json")).findFirst().orElseThrow());
        assertThat((String) JsonPath.read(disk, "$.textToModel.relativePath")).isEqualTo(LOGICAL_PATH);
        assertThat((String) JsonPath.read(disk, "$.textToModel.postTransformationId")).isEqualTo("transformation-t2m-post");
        // po: el modelo final es el proyectado (una entrada por nodo leido, con el valor fijo de la regla) y no el leido.
        assertThat(result.projectedModel().metamodelId()).isEqualTo(TARGET_METAMODEL);
        assertThat(result.projectedModel().elements()).hasSize(2);
        assertThat(result.projectedModel().elements()).allSatisfy(element -> {
            assertThat(element.classId()).isEqualTo(ENTRY);
            assertThat(element.attributes()).containsOnlyKeys(LABEL).containsEntry(LABEL, "projected");
        });
        assertThat(result.projectedModel().elements()).extracting(element -> element.annotations().get("hydra.transformation.source"))
                .containsExactlyInAnyOrder("alpha", "beta");
        assertThat(result.projectionTrace().transformationId()).isEqualTo("transformation-t2m-post");
        assertThat(result.projectionTrace().links()).extracting(link -> link.sourceElementId()).containsExactlyInAnyOrder("alpha", "beta");
        assertThat(result.projectedModel()).isNotEqualTo(result.parsedModel());
    }

    @Test
    @DisplayName("transformation_author_text2model.gr / x_grammar_to_text_model.t2m: otra definicion guardada con otra gramatica lee su propio texto y, sin transformacion posterior, el modelo final es el leido")
    void anotherSavedDefinitionUsesItsOwnGrammarAndWithoutPostTransformationReturnsTheReadModel(@TempDir Path temporary) {
        wire(temporary);
        GrammarCompilationSnapshot nodes = createAndCompile("t2m-own-nodes", NODE_GRAMMAR, true);
        GrammarCompilationSnapshot items = createAndCompile("t2m-own-items", ITEM_GRAMMAR, true);
        transformations.create(t2mDocument("t2m-saved-items", "t2m-own-items", "model.hydra", ""));
        transformations.create(t2mDocument("t2m-saved-nodes", "t2m-own-nodes", "model.hydra", ""));

        var itemResult = t2m.execute("t2m-saved-items", "item one\nitem two\n");
        var wrongGrammar = t2m.execute("t2m-saved-nodes", "item one\n");

        assertThat(itemResult.diagnostics()).isEmpty();
        assertThat(itemResult.grammarSnapshotId()).isEqualTo(items.id()).isNotEqualTo(nodes.id());
        assertThat(itemResult.parsedModel().elements()).extracting(CanonicalModelElement::id).containsExactly("one", "two");
        assertThat(itemResult.projectedModel()).isEqualTo(itemResult.parsedModel());
        assertThat(itemResult.projectionTrace()).isNull();
        // El mismo texto no vale para la gramatica de la otra definicion: diagnosticos y ningun modelo.
        assertThat(wrongGrammar.grammarSnapshotId()).isEqualTo(nodes.id());
        assertThat(wrongGrammar.diagnostics()).isNotEmpty();
        assertThat(wrongGrammar.parsedModel()).isNull();
        assertThat(wrongGrammar.projectedModel()).isNull();
    }

    @Test
    @DisplayName("transformation_author_text2model.gr / .pt / .po: un texto invalido no llega a la transformacion posterior; gramatica sin activar, ruta insegura o transformacion posterior inexistente se rechazan")
    void invalidTextInactiveGrammarUnsafePathAndMissingPostTransformationAreRejected(@TempDir Path temporary) throws Exception {
        wire(temporary);
        createAndCompile("t2m-reject-nodes", NODE_GRAMMAR, true);
        createAndCompile("t2m-reject-inactive", NODE_GRAMMAR, false);
        transformations.create(t2mDocument("t2m-saved-missing-post", "t2m-reject-nodes", "model.hydra", "transformation-does-not-exist"));
        transformations.create(t2mDocument("t2m-saved-inactive", "t2m-reject-inactive", "model.hydra", ""));
        transformations.create(t2mDocument("t2m-saved-unsafe", "t2m-reject-nodes", "../escape.hydra", ""));

        // Texto invalido: se informa y no se ejecuta la transformacion posterior (que ni siquiera existe).
        var invalid = t2m.execute("t2m-saved-missing-post", "node alpha\nnode 123\n");
        assertThat(invalid.parsedModel()).isNull();
        assertThat(invalid.projectedModel()).isNull();
        assertThat(invalid.projectionTrace()).isNull();
        assertThat(invalid.diagnostics()).isNotEmpty();

        // Texto valido pero transformacion posterior inexistente: falla sin devolver un modelo a medias.
        assertThatThrownBy(() -> t2m.execute("t2m-saved-missing-post", "node alpha\n"))
                .isInstanceOf(TransformationApplicationException.class)
                .extracting(exception -> ((TransformationApplicationException) exception).getReason())
                .isEqualTo(TransformationApplicationException.Reason.NOT_FOUND);
        assertThatThrownBy(() -> t2m.execute("t2m-saved-inactive", "node alpha\n"))
                .isInstanceOf(IllegalStateException.class).hasMessage("No active successful grammar snapshot for t2m-reject-inactive");
        assertThatThrownBy(() -> t2m.execute("t2m-saved-unsafe", "node alpha\n"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("relative and portable");
        assertThatThrownBy(() -> t2m.execute("t2m-not-saved", "node alpha\n"))
                .isInstanceOf(TransformationApplicationException.class)
                .extracting(exception -> ((TransformationApplicationException) exception).getReason())
                .isEqualTo(TransformationApplicationException.Reason.NOT_FOUND);
        assertThat(filesNamed(temporary.resolve("transformations"), "t2m-not-saved")).isEmpty();
    }
}
