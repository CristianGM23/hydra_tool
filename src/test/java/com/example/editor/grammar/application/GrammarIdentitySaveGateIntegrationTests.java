package com.example.editor.grammar.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.editor.grammar.GrammarProperties;
import com.example.editor.grammar.core.GrammarAnalysisMode;
import com.example.editor.grammar.core.GrammarAnalysisRequest;
import com.example.editor.grammar.core.GrammarAnalysisResult;
import com.example.editor.grammar.core.GrammarAuthoringMode;
import com.example.editor.grammar.core.GrammarDefinitionModel;
import com.example.editor.grammar.core.GrammarSemanticBindingMap;
import com.example.editor.grammar.core.GrammarSourceDocument;
import com.example.editor.grammar.core.GrammarTechnology;
import com.example.editor.grammar.core.GrammarToolchainProfile;
import com.example.editor.grammar.persistence.JsonGrammarRepository;
import com.example.editor.grammar.provider.GrammarProviderRegistry;
import com.example.editor.grammar.provider.antlr.AntlrGrammarProvider;
import com.example.editor.grammar.provider.langium.LangiumGrammarProvider;
import com.example.editor.grammar.provider.xtext.XtextGrammarProvider;
import com.example.editor.persistence.LocalCanonicalDocumentStoreFactory;
import tools.jackson.databind.json.JsonMapper;

/**
 * Evidencia real de la puerta de guardado de la identidad de una gramatica: grammar_author_identity.id
 * (identificador y nombre validos y unicos), grammar_author_identity.tc (perfil de herramientas
 * existente y compatible) y grammar_author_identity.er (regla de entrada obligatoria; analisis con
 * entrada explicita o inferida). Repositorio JSON real en un directorio temporal y proveedores reales.
 */
class GrammarIdentitySaveGateIntegrationTests {

    private GrammarApplicationService grammars;
    private GrammarToolchainProfileService profiles;
    private GrammarProperties properties;
    private Path grammarDirectory;

    private void wire(Path temporary) {
        properties = new GrammarProperties();
        grammarDirectory = temporary.resolve("grammars");
        properties.setDirectory(grammarDirectory);
        properties.setCompilationDirectory(temporary.resolve("grammar-compilations"));
        properties.setActivationDirectory(temporary.resolve("grammar-activations"));
        JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();
        profiles = new GrammarToolchainProfileService();
        GrammarProviderRegistry providers = new GrammarProviderRegistry(List.of(
                new AntlrGrammarProvider(properties), new LangiumGrammarProvider(properties), new XtextGrammarProvider(properties)));
        grammars = new GrammarApplicationService(new JsonGrammarRepository(properties, mapper, new LocalCanonicalDocumentStoreFactory()),
                properties, profiles, providers);
    }

    private static GrammarDefinitionModel grammar(String id, String name, String profileId, String entryRule) {
        GrammarSourceDocument document = new GrammarSourceDocument("main", "main.langium", "langium",
                "grammar Stable\nentry Model: nodes+=Node*;\nNode: 'node' name=ID;\n", "", true, false);
        return new GrammarDefinitionModel("1.0", id, name, GrammarTechnology.LANGIUM, GrammarAuthoringMode.GRAMMAR_FIRST, "", profileId,
                "main", entryRule, List.of(document), GrammarSemanticBindingMap.empty(), "", Map.of(), Map.of());
    }

    private long storedFiles() throws IOException {
        if (!Files.exists(grammarDirectory)) return 0;
        try (Stream<Path> walk = Files.walk(grammarDirectory)) {
            return walk.filter(Files::isRegularFile).count();
        }
    }

    private boolean anyStoredFileMentions(String text) throws IOException {
        if (!Files.exists(grammarDirectory)) return false;
        try (Stream<Path> walk = Files.walk(grammarDirectory)) {
            return walk.filter(Files::isRegularFile).anyMatch(path -> {
                try {
                    return Files.readString(path, StandardCharsets.UTF_8).contains(text);
                } catch (IOException exception) {
                    throw new IllegalStateException(exception);
                }
            });
        }
    }

    @Test
    @DisplayName("grammar_author_identity.id: identificadores y nombres invalidos o repetidos se rechazan sin guardar nada")
    void invalidIdentifiersBlankNamesAndDuplicateNamesAreRejectedWithNothingStored(@TempDir Path temporary) throws IOException {
        wire(temporary);

        for (String invalidId : List.of("my grammar", "1grammar", "g")) {
            assertThatThrownBy(() -> grammars.create(grammar(invalidId, "Some name", "langium-4.3-browser", "Model")))
                    .isInstanceOf(IllegalArgumentException.class).hasMessage("Grammar id is invalid");
        }
        assertThatThrownBy(() -> grammars.create(grammar("blank-name", "   ", "langium-4.3-browser", "Model")))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Grammar name is required");
        assertThat(storedFiles()).isZero();
        assertThat(grammars.list()).isEmpty();

        grammars.create(grammar("first-grammar", "Shared Name", "langium-4.3-browser", "Model"));
        assertThatThrownBy(() -> grammars.create(grammar("second-grammar", "shared name", "langium-4.3-browser", "Model")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Grammar name already exists");
        assertThat(grammars.list()).extracting(summary -> summary.id()).containsExactly("first-grammar");
        assertThat(anyStoredFileMentions("second-grammar")).isFalse();
    }

    @Test
    @DisplayName("grammar_author_identity.tc: un perfil de herramientas desconocido o de otra tecnologia se rechaza; el catalogo ofrece los perfiles")
    void unknownOrIncompatibleToolchainProfilesAreRejectedAndTheCatalogListsTheOfferedOnes(@TempDir Path temporary) throws IOException {
        wire(temporary);

        assertThat(profiles.list()).extracting(GrammarToolchainProfile::id)
                .containsExactly("antlr-4.13.2-java", "antlr-4.13.2-typescript", "langium-4.3-browser", "langium-4.4-browser", "xtext-2.44-lsp");

        assertThatThrownBy(() -> grammars.create(grammar("unknown-profile", "Unknown profile", "does-not-exist", "Model")))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Unknown grammar toolchain profile: does-not-exist");
        assertThatThrownBy(() -> grammars.create(grammar("wrong-profile", "Wrong profile", "antlr-4.13.2-java", "Model")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("is not compatible with LANGIUM");
        assertThat(storedFiles()).isZero();

        // Un perfil en blanco se resuelve al perfil por defecto de la tecnologia y se acepta.
        grammars.create(grammar("default-profile", "Default profile", "", "Model"));
        assertThat(grammars.find("default-profile").grammar().id()).isEqualTo("default-profile");
    }

    @Test
    @DisplayName("grammar_author_identity.er: sin regla de entrada no se guarda; con ella se guarda y se relee desde disco")
    void aBlankEntryRuleIsRejectedAndAValidIdentityIsPersistedAndReadBack(@TempDir Path temporary) throws IOException {
        wire(temporary);

        assertThatThrownBy(() -> grammars.create(grammar("no-entry", "No entry", "langium-4.3-browser", "  ")))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Grammar entryRule is required");
        assertThat(storedFiles()).isZero();

        grammars.create(grammar("valid-grammar", "Valid grammar", "langium-4.3-browser", "Model"));

        assertThat(storedFiles()).isGreaterThanOrEqualTo(1);
        assertThat(anyStoredFileMentions("valid-grammar")).isTrue();
        GrammarDefinitionModel reread = grammars.find("valid-grammar").grammar();
        assertThat(reread.name()).isEqualTo("Valid grammar");
        assertThat(reread.entryRule()).isEqualTo("Model");
        assertThat(reread.toolchainProfileId()).isEqualTo("langium-4.3-browser");
    }

    @Test
    @DisplayName("grammar_author_identity.er: el analisis distingue la regla de entrada explicita de la inferida")
    void analysisReportsDifferentEntryRuleMethodsForExplicitAndInferredEntries(@TempDir Path temporary) {
        wire(temporary);
        GrammarSourceAnalyzer analyzer = new GrammarSourceAnalyzer(properties);

        GrammarAnalysisResult explicit = analyze(analyzer, "grammar A\nentry Model: elements+=Element*;\nElement: 'element' name=ID;\n");
        GrammarAnalysisResult inferred = analyze(analyzer, "grammar A\nModel: elements+=Element*;\nElement: 'element' name=ID;\n");

        assertThat(explicit.metadata()).containsEntry("entryRule.method", "EXPLICIT_ENTRY").containsEntry("entryRule.inferred", "Model");
        assertThat(inferred.metadata()).containsEntry("entryRule.inferred", "Model");
        assertThat(inferred.metadata().get("entryRule.method")).isNotEqualTo("EXPLICIT_ENTRY");
        assertThat(inferred.metadata().get("entryRule.method")).isNotEqualTo(explicit.metadata().get("entryRule.method"));
    }

    private static GrammarAnalysisResult analyze(GrammarSourceAnalyzer analyzer, String source) {
        GrammarSourceDocument document = new GrammarSourceDocument("main", "main.langium", "langium", source, "", true, false);
        GrammarDefinitionModel model = new GrammarDefinitionModel("1.0", "entry-analysis", "Entry analysis", GrammarTechnology.LANGIUM,
                GrammarAuthoringMode.GRAMMAR_FIRST, "", "langium-4.3-browser", "main", "Model", List.of(document),
                GrammarSemanticBindingMap.empty(), "", Map.of(), Map.of());
        return analyzer.analyze(model, new GrammarAnalysisRequest(model.id(), 1, GrammarAnalysisMode.NORMAL, List.of("main"),
                model.documents(), model.toolchainProfileId()));
    }
}
