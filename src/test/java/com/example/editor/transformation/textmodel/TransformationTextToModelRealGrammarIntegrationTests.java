package com.example.editor.transformation.textmodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
import com.example.editor.grammar.runtime.LastKnownGoodGrammarService;
import com.example.editor.grammar.visualization.GrammarClassDiagramProjectionService;
import com.example.editor.integration.verification.ExternalToolCircuitBreaker;
import com.example.editor.integration.verification.ExternalToolProcessRunner;
import com.example.editor.persistence.LocalCanonicalDocumentStoreFactory;
import com.example.editor.security.process.HydraExternalProcessPolicy;
import com.example.editor.security.process.HydraExternalProcessSecurityProperties;
import com.example.editor.security.process.HydraTemporaryWorkspace;
import com.example.editor.semantic.expression.ExpressionDependencyAnalyzer;
import com.example.editor.semantic.expression.ExpressionTypeChecker;
import com.example.editor.transformation.TransformationDefinitionChecker;
import com.example.editor.transformation.core.TransformationDefinitionDocument;
import com.example.editor.transformation.core.TransformationKind;
import com.example.editor.transformation.core.TransformationTextToModelConfiguration;
import com.example.editor.transformation.runtime.TransformationExecutionException;
import com.example.editor.validation.model.CanonicalModelElement;
import tools.jackson.databind.json.JsonMapper;

/**
 * Evidencia real de la vista previa de una transformacion de texto a modelo contra una gramatica Langium
 * real compilada y activada (sin herramientas nativas): transformation_author_text2model.gr (se usa el
 * snapshot activo de la gramatica elegida), .pt (la ruta logica del texto, por defecto model.hydra, se valida),
 * y .ck (sin gramatica la comprobacion bloquea). La transformacion posterior de modelo a modelo no se ejerce.
 */
class TransformationTextToModelRealGrammarIntegrationTests {

    private static final String SOURCE = "grammar Stable\nentry Model: nodes+=Node*;\nNode: 'node' name=ID;\nhidden terminal WS: /\\s+/;\nterminal ID: /[_a-zA-Z][\\w_]*/;\n";

    private GrammarApplicationService grammars;
    private GrammarCompilationService compilations;
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
        TransformationDefinitionChecker checker = new TransformationDefinitionChecker(new ExpressionTypeChecker(new ExpressionDependencyAnalyzer()));
        t2m = new TransformationTextToModelService(null, checker, new GrammarTransformationTextParserGateway(new LastKnownGoodGrammarService(compilations)), null);
    }

    private GrammarCompilationSnapshot createAndCompile(String id, boolean activate) {
        GrammarSourceDocument document = new GrammarSourceDocument("main", "main.langium", "langium", SOURCE, "", true, false);
        grammars.create(new GrammarDefinitionModel("1.0", id, id, GrammarTechnology.LANGIUM, GrammarAuthoringMode.GRAMMAR_FIRST, "",
                "langium-4.4-browser", "main", "Model", List.of(document), GrammarSemanticBindingMap.empty(), "", Map.of(), Map.of()));
        GrammarCompilationSnapshot snapshot = compilations.compile(id, new GrammarCompilationRequest(id, 1, List.of(document), "langium-4.4-browser", activate));
        assertThat(snapshot.status()).isEqualTo(CompilationStatus.SUCCESS);
        return snapshot;
    }

    private static TransformationDefinitionDocument t2mDocument(String grammarId, String path) {
        return new TransformationDefinitionDocument("1.1", "transformation-text2model", "Text to model", TransformationKind.T2M,
                List.of(), List.of(), List.of(), List.of(), new TransformationTextToModelConfiguration(grammarId, path, ""), null, null, Map.of());
    }

    @Test
    @DisplayName("transformation_author_text2model.gr: con una gramatica activa el texto valido produce el modelo semantico con sus nodos y el snapshot activo")
    void validTextProducesTheSemanticModelFromTheActiveGrammarSnapshot(@TempDir Path temporary) {
        wire(temporary);
        GrammarCompilationSnapshot active = createAndCompile("t2m-grammar", true);

        var result = t2m.preview(t2mDocument("t2m-grammar", "model.hydra"), "node alpha\nnode beta\n");

        assertThat(result.diagnostics()).isEmpty();
        assertThat(result.grammarSnapshotId()).isEqualTo(active.id());
        assertThat(result.parsedModel()).isNotNull();
        assertThat(result.parsedModel().elements()).extracting(CanonicalModelElement::id).containsExactly("alpha", "beta");
        assertThat(result.projectedModel()).isNotNull();
        assertThat(result.projectedModel().elements()).extracting(CanonicalModelElement::id).containsExactly("alpha", "beta");
        assertThat(result.projectionTrace()).isNull();

        // Una ruta logica en blanco toma el valor por defecto model.hydra y la vista previa sigue funcionando.
        TransformationDefinitionDocument withDefaultPath = t2mDocument("t2m-grammar", "  ");
        assertThat(withDefaultPath.textToModel().relativePath()).isEqualTo("model.hydra");
        assertThat(t2m.preview(withDefaultPath, "node gamma\n").parsedModel().elements()).extracting(CanonicalModelElement::id).containsExactly("gamma");
    }

    @Test
    @DisplayName("transformation_author_text2model.gr: un texto invalido devuelve diagnosticos con gravedad y ningun modelo")
    void invalidTextReturnsDiagnosticsAndNoModel(@TempDir Path temporary) {
        wire(temporary);
        GrammarCompilationSnapshot active = createAndCompile("t2m-invalid", true);

        var result = t2m.preview(t2mDocument("t2m-invalid", "model.hydra"), "node alpha\nnode 123\n");

        assertThat(result.parsedModel()).isNull();
        assertThat(result.projectedModel()).isNull();
        assertThat(result.grammarSnapshotId()).isEqualTo(active.id());
        assertThat(result.diagnostics()).isNotEmpty();
        assertThat(result.diagnostics()).allSatisfy(diagnostic -> assertThat(diagnostic.severity()).isEqualTo(GrammarSeverity.ERROR));
        assertThat(result.diagnostics()).extracting(diagnostic -> diagnostic.code()).contains("TEXTUAL_GRAMMAR_MISMATCH");
    }

    @Test
    @DisplayName("transformation_author_text2model.ck: sin gramatica asignada la comprobacion bloquea; con gramatica sin activar o ruta insegura tambien falla")
    void withoutAnActiveGrammarOrWithAnUnsafePathThePreviewIsRejected(@TempDir Path temporary) {
        wire(temporary);
        createAndCompile("t2m-inactive", false);

        assertThatThrownBy(() -> t2m.preview(t2mDocument("", "model.hydra"), "node alpha\n"))
                .isInstanceOf(TransformationExecutionException.class).hasMessage("T2M preview contains blocking diagnostics");
        assertThatThrownBy(() -> t2m.preview(t2mDocument("t2m-inactive", "model.hydra"), "node alpha\n"))
                .isInstanceOf(IllegalStateException.class).hasMessage("No active successful grammar snapshot for t2m-inactive");
        assertThatThrownBy(() -> t2m.preview(t2mDocument("t2m-never-compiled", "model.hydra"), "node alpha\n"))
                .isInstanceOf(IllegalStateException.class).hasMessage("No active successful grammar snapshot for t2m-never-compiled");

        createAndCompile("t2m-path", true);
        assertThatThrownBy(() -> t2m.preview(t2mDocument("t2m-path", "../escape.hydra"), "node alpha\n"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("relative and portable");
    }
}
