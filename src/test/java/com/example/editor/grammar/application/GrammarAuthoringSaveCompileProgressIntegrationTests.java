package com.example.editor.grammar.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.editor.grammar.GrammarProperties;
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
import com.example.editor.grammar.runtime.GrammarCompilationProgress;
import com.example.editor.grammar.runtime.GrammarCompilationSnapshot;
import com.example.editor.grammar.visualization.GrammarClassDiagramProjectionService;
import com.example.editor.integration.verification.ExternalToolCircuitBreaker;
import com.example.editor.integration.verification.ExternalToolProcessRunner;
import com.example.editor.persistence.LocalCanonicalDocumentStoreFactory;
import com.example.editor.security.process.HydraExternalProcessPolicy;
import com.example.editor.security.process.HydraExternalProcessSecurityProperties;
import com.example.editor.security.process.HydraTemporaryWorkspace;
import com.example.editor.validation.persistence.CanonicalDocumentStoreException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Evidencia real de dos etapas del autor de gramaticas: grammar_author_save_compile.sv (Guardar no compila:
 * guardar con ETag vigente o caducada solo toca el borrador, y no aparece ninguna compilacion ni activacion)
 * y grammar_author_save_compile.pg (el progreso de compilacion llega a exito con doce de doce etapas, o a
 * fallo con la etapa fallida, sin tocar la version activa). Cableado real igual que el de la prueba de
 * compilacion de doce etapas.
 */
class GrammarAuthoringSaveCompileProgressIntegrationTests {

    private GrammarApplicationService grammars;
    private GrammarCompilationService service;
    private GrammarCompilationProgressService progress;
    private Path compilationDirectory;

    private void wire(Path temporary) {
        GrammarProperties properties = new GrammarProperties();
        properties.setDirectory(temporary.resolve("grammars"));
        compilationDirectory = temporary.resolve("grammar-compilations");
        properties.setCompilationDirectory(compilationDirectory);
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
        progress = new GrammarCompilationProgressService(properties);
        service = new GrammarCompilationService(grammars, profiles, providers, analysis,
                new JsonGrammarCompilationRepository(properties, mapper, stores), new JsonGrammarActivationRepository(properties, mapper, stores),
                nativeToolchain, new GrammarClassDiagramProjectionService(properties), properties, progress);
    }

    private static GrammarSourceDocument document(String source) {
        return new GrammarSourceDocument("main", "main.langium", "langium", source, "", true, false);
    }

    private static GrammarDefinitionModel grammar(String id, String name, GrammarSourceDocument document) {
        return new GrammarDefinitionModel("1.0", id, name, GrammarTechnology.LANGIUM, GrammarAuthoringMode.GRAMMAR_FIRST, "",
                "langium-4.3-browser", "main", "Model", List.of(document), GrammarSemanticBindingMap.empty(), "", Map.of(), Map.of());
    }

    private static final String VALID = "grammar Stable\nentry Model: nodes+=Node*;\nNode: 'node' name=ID;\n";

    private long filesUnder(Path directory) throws IOException {
        if (!Files.exists(directory)) return 0;
        try (Stream<Path> walk = Files.walk(directory)) {
            return walk.filter(Files::isRegularFile).count();
        }
    }

    @Test
    @DisplayName("grammar_author_save_compile.sv: guardar con ETag vigente actualiza el borrador, con ETag caducada se rechaza, y guardar no compila")
    void savingUpdatesTheDraftHonoursTheETagAndNeverStartsACompilation(@TempDir Path temporary) throws IOException {
        wire(temporary);
        GrammarSaveResponse created = grammars.create(grammar("saved", "Saved grammar", document(VALID)));

        GrammarSaveResponse replaced = grammars.replace("saved", grammar("saved", "Saved grammar renamed", document(VALID)), created.etag());
        assertThat(replaced.etag()).isNotEqualTo(created.etag());
        assertThat(grammars.find("saved").grammar().name()).isEqualTo("Saved grammar renamed");

        assertThatThrownBy(() -> grammars.replace("saved", grammar("saved", "Lost update", document(VALID)), created.etag()))
                .isInstanceOf(CanonicalDocumentStoreException.class);
        assertThat(grammars.find("saved").grammar().name()).isEqualTo("Saved grammar renamed");
        assertThat(grammars.find("saved").etag()).isEqualTo(replaced.etag());

        // Guardar no compila: ni snapshots listados, ni version activa, ni un solo fichero de compilacion en disco.
        assertThat(service.list("saved")).isEmpty();
        assertThat(service.active("saved")).isEmpty();
        assertThat(filesUnder(compilationDirectory)).isZero();
    }

    @Test
    @DisplayName("grammar_author_save_compile.pg: el progreso de una compilacion correcta llega a SUCCESS con 12/12 etapas; sin activar no hay version activa")
    void progressOfASuccessfulCompilationReachesTwelveOfTwelveStages(@TempDir Path temporary) {
        wire(temporary);
        GrammarSourceDocument document = document(VALID);
        grammars.create(grammar("progress", "Progress grammar", document));

        GrammarCompilationSnapshot inactive = service.compile("progress", new GrammarCompilationRequest("req-inactive", "progress", 1,
                List.of(document), "langium-4.3-browser", "", false, Map.of()));
        assertThat(inactive.status()).isEqualTo(CompilationStatus.SUCCESS);
        assertThat(service.active("progress")).isEmpty();
        GrammarCompilationProgress inactiveProgress = progress.find("req-inactive").orElseThrow();
        assertThat(inactiveProgress.status()).isEqualTo("SUCCESS");
        assertThat(inactiveProgress.completedStages()).isEqualTo(12);
        assertThat(inactiveProgress.totalStages()).isEqualTo(12);

        GrammarCompilationSnapshot active = service.compile("progress", new GrammarCompilationRequest("req-active", "progress", 1,
                List.of(document), "langium-4.3-browser", "", true, Map.of()));
        assertThat(active.status()).isEqualTo(CompilationStatus.SUCCESS);
        assertThat(service.active("progress")).isPresent().get().extracting(GrammarCompilationSnapshot::id).isEqualTo(active.id());
        GrammarCompilationProgress activeProgress = progress.find("req-active").orElseThrow();
        assertThat(activeProgress.grammarId()).isEqualTo("progress");
        assertThat(activeProgress.status()).isEqualTo("SUCCESS");
        assertThat(activeProgress.completedStages()).isEqualTo(12);
        assertThat(service.list("progress")).extracting(GrammarCompilationSnapshot::id).containsExactlyInAnyOrder(inactive.id(), active.id());
    }

    @Test
    @DisplayName("grammar_author_save_compile.pg: el progreso de una compilacion rota termina en FAILED con la etapa y la version activa queda intacta")
    void progressOfABrokenCompilationFailsAtAStageAndKeepsTheActiveVersion(@TempDir Path temporary) {
        wire(temporary);
        GrammarSourceDocument good = document(VALID);
        grammars.create(grammar("regress", "Regress grammar", good));
        GrammarCompilationSnapshot first = service.compile("regress", new GrammarCompilationRequest("req-good", "regress", 1,
                List.of(good), "langium-4.3-browser", "", true, Map.of()));
        assertThat(first.status()).isEqualTo(CompilationStatus.SUCCESS);

        GrammarSourceDocument broken = document("!!! this is not a valid langium grammar !!!");
        GrammarCompilationSnapshot failed = service.compile("regress", new GrammarCompilationRequest("req-broken", "regress", 2,
                List.of(broken), "langium-4.3-browser", "", true, Map.of()));

        assertThat(failed.status()).isEqualTo(CompilationStatus.FAILED);
        GrammarCompilationProgress failure = progress.find("req-broken").orElseThrow();
        assertThat(failure.status()).isEqualTo("FAILED");
        assertThat(failure.stage()).isNotBlank();
        assertThat(failure.completedStages()).isLessThan(12);
        assertThat(service.active("regress")).isPresent().get().extracting(GrammarCompilationSnapshot::id).isEqualTo(first.id());
        assertThat(progress.find("req-good").orElseThrow().status()).isEqualTo("SUCCESS");
    }
}
