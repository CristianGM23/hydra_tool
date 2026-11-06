package com.example.editor.grammar.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.example.editor.grammar.GrammarProperties;
import com.example.editor.grammar.core.GrammarAnalysisMode;
import com.example.editor.grammar.core.GrammarAnalysisRequest;
import com.example.editor.grammar.core.GrammarAuthoringMode;
import com.example.editor.grammar.core.GrammarDefinitionModel;
import com.example.editor.grammar.core.GrammarDraftAnalysisRequest;
import com.example.editor.grammar.core.GrammarSemanticBinding;
import com.example.editor.grammar.core.GrammarSemanticBindingMap;
import com.example.editor.grammar.core.GrammarSourceDocument;
import com.example.editor.grammar.core.GrammarTechnology;
import com.example.editor.grammar.persistence.JsonGrammarRepository;
import com.example.editor.grammar.provider.GrammarProviderRegistry;
import com.example.editor.grammar.provider.antlr.AntlrGrammarProvider;
import com.example.editor.grammar.provider.langium.LangiumGrammarProvider;
import com.example.editor.grammar.provider.xtext.XtextGrammarProvider;
import com.example.editor.grammar.runtime.GrammarProviderHealthService;
import com.example.editor.grammar.visualization.GrammarClassDiagramProjectionService;
import com.example.editor.grammar.web.GrammarController;
import com.example.editor.persistence.LocalCanonicalDocumentStoreFactory;
import com.example.editor.web.ApiExceptionHandler;
import tools.jackson.databind.json.JsonMapper;

/**
 * Evidencia real del analisis de borradores de gramatica a traves de la capa HTTP del autor: etapas
 * grammar_analyze_generate.fast (analisis rapido), .rg (grafo de reglas: recursion por la izquierda
 * bloquea, por la derecha no), .rf (referencias y correspondencias), .pj (proyeccion del diagrama de
 * clases, solo lectura) y grammar_author_monaco.an (los problemas llegan al panel con diagnosticos y
 * acciones). Coordinador de analisis, proveedores y proyector reales; nada se escribe en disco.
 */
class GrammarDraftAnalysisPipelineIntegrationTests {

    private MockMvc mockMvc;
    private JsonMapper mapper;
    private GrammarAnalysisCoordinator coordinator;
    private Path root;

    private void wire(Path temporary) {
        root = temporary;
        GrammarProperties properties = new GrammarProperties();
        properties.setDirectory(temporary.resolve("grammars"));
        properties.setCompilationDirectory(temporary.resolve("grammar-compilations"));
        properties.setActivationDirectory(temporary.resolve("grammar-activations"));
        mapper = JsonMapper.builder().findAndAddModules().build();
        GrammarToolchainProfileService profiles = new GrammarToolchainProfileService();
        GrammarProviderRegistry providers = new GrammarProviderRegistry(List.of(
                new AntlrGrammarProvider(properties), new LangiumGrammarProvider(properties), new XtextGrammarProvider(properties)));
        GrammarApplicationService grammars = new GrammarApplicationService(
                new JsonGrammarRepository(properties, mapper, new LocalCanonicalDocumentStoreFactory()), properties, profiles, providers);
        GrammarProviderHealthService health = new GrammarProviderHealthService(properties);
        coordinator = new GrammarAnalysisCoordinator(profiles, providers, new GrammarAnalysisCache(properties), health, properties);
        GrammarClassDiagramProjectionService diagrams = new GrammarClassDiagramProjectionService(properties);
        GrammarController controller = new GrammarController(grammars, new GrammarAnalysisService(grammars, coordinator), profiles, providers,
                health, diagrams, null);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new ApiExceptionHandler()).build();
    }

    @AfterEach
    void closeCoordinator() {
        if (coordinator != null) coordinator.close();
    }

    private static GrammarDefinitionModel grammar(String id, String entryRule, String source, GrammarSemanticBindingMap bindings) {
        GrammarSourceDocument document = new GrammarSourceDocument("main", "main.langium", "langium", source, "", true, false);
        return new GrammarDefinitionModel("1.0", id, id, GrammarTechnology.LANGIUM, GrammarAuthoringMode.GRAMMAR_FIRST, "",
                "langium-4.3-browser", "main", entryRule, List.of(document), bindings, "", Map.of(), Map.of());
    }

    private String draftBody(GrammarDefinitionModel grammar, GrammarAnalysisMode mode) throws Exception {
        GrammarAnalysisRequest analysis = new GrammarAnalysisRequest(grammar.id(), 1, mode, List.of("main"), grammar.documents(), grammar.toolchainProfileId());
        return mapper.writeValueAsString(new GrammarDraftAnalysisRequest(grammar, analysis));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> analyzeVia(String path, GrammarDefinitionModel grammar, GrammarAnalysisMode mode) throws Exception {
        String body = mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(draftBody(grammar, mode)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return mapper.readValue(body, Map.class);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> list(Map<String, Object> source, String key) {
        return (List<Map<String, Object>>) source.get(key);
    }

    private static boolean hasDiagnostic(Map<String, Object> result, String code, String severity) {
        return list(result, "diagnostics").stream().anyMatch(item -> code.equals(item.get("code")) && severity.equals(item.get("severity")));
    }

    private static boolean hasAnyError(Map<String, Object> result) {
        return list(result, "diagnostics").stream().anyMatch(item -> "ERROR".equals(item.get("severity")));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> metadata(Map<String, Object> result) {
        return (Map<String, String>) result.get("metadata");
    }

    @Test
    @DisplayName("grammar_analyze_generate.fast + grammar_author_monaco.an: un borrador valido se analiza sin errores y devuelve simbolos")
    void aValidDraftIsAnalyzedWithoutErrorsAndReturnsItsSymbols(@TempDir Path temporary) throws Exception {
        wire(temporary);

        Map<String, Object> result = analyzeVia("/api/grammars/analyze-draft",
                grammar("draft-valid", "Model", "grammar Stable\nentry Model: nodes+=Node*;\nNode: 'node' name=ID;\n", GrammarSemanticBindingMap.empty()),
                GrammarAnalysisMode.FAST);

        assertThat(hasAnyError(result)).isFalse();
        assertThat(result.get("stale")).isEqualTo(false);
        assertThat(list(result, "symbols")).extracting(symbol -> symbol.get("name")).contains("Model", "Node");
        assertThat(metadata(result)).containsEntry("entryRule.inferred", "Model");
    }

    @Test
    @DisplayName("grammar_analyze_generate.rf + grammar_author_monaco.an: referencia ausente con candidato, referencia ambigua y destino obsoleto")
    void missingAmbiguousAndStaleReferencesAreReportedWithCandidatesAndActions(@TempDir Path temporary) throws Exception {
        wire(temporary);

        Map<String, Object> missing = analyzeVia("/api/grammars/analyze-draft",
                grammar("draft-missing", "Model", "grammar Stable\nentry Model: nodes+=Nodes*;\nNode: 'node' name=ID;\n", GrammarSemanticBindingMap.empty()),
                GrammarAnalysisMode.NORMAL);
        assertThat(hasDiagnostic(missing, "REFERENCE_NOT_FOUND", "ERROR")).isTrue();
        assertThat(list(missing, "diagnostics")).anySatisfy(item -> {
            assertThat(item.get("code")).isEqualTo("REFERENCE_NOT_FOUND");
            assertThat(list(item, "candidates")).extracting(candidate -> candidate.get("name")).contains("Node");
        });
        assertThat(list(missing, "codeActions")).anyMatch(action -> "quickfix.reference.replace".equals(action.get("kind")));

        Map<String, Object> ambiguous = analyzeVia("/api/grammars/analyze-draft",
                grammar("draft-ambiguous", "Model", "grammar Stable\nentry Model: nodes+=Node*;\nNode: 'node' name=ID;\nNode: 'other' name=ID;\n", GrammarSemanticBindingMap.empty()),
                GrammarAnalysisMode.NORMAL);
        assertThat(hasDiagnostic(ambiguous, "REFERENCE_AMBIGUOUS", "ERROR")).isTrue();
        assertThat(hasDiagnostic(ambiguous, "GRAMMAR_DUPLICATE_SYMBOL", "ERROR")).isTrue();

        GrammarSemanticBinding obsolete = new GrammarSemanticBinding("rule:Ghost", "RULE", "Ghost", "CLASS", 0.9d, "manual", Map.of("targetRevision", "3"));
        Map<String, Object> stale = analyzeVia("/api/grammars/analyze-draft",
                grammar("draft-stale", "Model", "grammar Stable\nentry Model: nodes+=Node*;\nNode: 'node' name=ID;\n",
                        new GrammarSemanticBindingMap("1.0", List.of(obsolete), Map.of())),
                GrammarAnalysisMode.NORMAL);
        assertThat(hasDiagnostic(stale, "REFERENCE_STALE", "ERROR")).isTrue();
    }

    @Test
    @DisplayName("grammar_analyze_generate.rg: la recursion por la izquierda bloquea, la recursion por la derecha no")
    void leftRecursionBlocksWhileRightRecursionAndUnusedRulesDoNot(@TempDir Path temporary) throws Exception {
        wire(temporary);

        Map<String, Object> left = analyzeVia("/api/grammars/analyze-draft",
                grammar("draft-left", "Model", "grammar Calc\nentry Model: expression=Expression;\nExpression: Expression '+' INT | INT;\n", GrammarSemanticBindingMap.empty()),
                GrammarAnalysisMode.NORMAL);
        assertThat(hasDiagnostic(left, "GRAMMAR_LEFT_RECURSION", "ERROR")).isTrue();
        assertThat(metadata(left)).containsEntry("recursion.leftCount", "1");

        Map<String, Object> right = analyzeVia("/api/grammars/analyze-draft",
                grammar("draft-right", "Model", "grammar Tree\nentry Model: root=Node;\nNode: 'node' name=ID ('child' child=Node)?;\n", GrammarSemanticBindingMap.empty()),
                GrammarAnalysisMode.NORMAL);
        assertThat(hasAnyError(right)).isFalse();
        assertThat(hasDiagnostic(right, "GRAMMAR_RECURSION_SAFE", "INFO")).isTrue();
        assertThat(metadata(right)).containsEntry("recursion.leftCount", "0");

        Map<String, Object> unused = analyzeVia("/api/grammars/analyze-draft",
                grammar("draft-unused", "Model", "grammar Stable\nentry Model: nodes+=Node*;\nNode: 'node' name=ID;\nLegacy: 'legacy';\n", GrammarSemanticBindingMap.empty()),
                GrammarAnalysisMode.NORMAL);
        assertThat(hasAnyError(unused)).isFalse();
        assertThat(hasDiagnostic(unused, "GRAMMAR_UNUSED_RULE", "WARNING")).isTrue();
        assertThat(metadata(unused)).containsEntry("unusedRules.names", "Legacy");
    }

    @Test
    @DisplayName("grammar_analyze_generate.pj: el diagrama de clases del borrador deduce clases, atributos y referencias y no escribe nada en disco")
    void theDraftClassDiagramIsProjectedFromTheDraftWithoutWritingAnything(@TempDir Path temporary) throws Exception {
        wire(temporary);

        Map<String, Object> projection = analyzeVia("/api/grammars/class-diagram-draft",
                grammar("draft-diagram", "Model", "grammar People\nentry Model: people+=Person*;\nPerson: 'person' name=ID '{' age=INT manager=[Person|ID] '}';\n",
                        GrammarSemanticBindingMap.empty()),
                GrammarAnalysisMode.NORMAL);

        assertThat(projection.get("status")).isEqualTo("COMPLETE");
        Map<String, Object> person = list(projection, "nodes").stream().filter(node -> "Person".equals(node.get("name"))).findFirst().orElseThrow();
        assertThat(list(person, "features")).extracting(feature -> feature.get("name")).contains("age", "manager");
        assertThat(list(projection, "edges")).anyMatch(edge -> "manager".equals(edge.get("label")) && "REFERENCE".equals(edge.get("kind")));
        assertThat(String.valueOf(projection.get("projectionHash"))).isNotBlank();
        try (Stream<Path> walk = Files.walk(root)) {
            assertThat(walk.filter(Files::isRegularFile).count()).isZero();
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
