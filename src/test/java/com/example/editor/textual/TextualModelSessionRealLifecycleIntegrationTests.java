package com.example.editor.textual;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.example.editor.grammar.core.GrammarAnalysisMode;
import com.example.editor.grammar.core.GrammarAnalysisRequest;
import com.example.editor.grammar.core.GrammarAuthoringMode;
import com.example.editor.grammar.core.GrammarCompilationRequest;
import com.example.editor.grammar.core.GrammarDefinitionModel;
import com.example.editor.grammar.core.GrammarDraftAnalysisRequest;
import com.example.editor.grammar.core.GrammarSemanticBindingMap;
import com.example.editor.grammar.core.GrammarSourceDocument;
import com.example.editor.grammar.core.GrammarTechnology;
import com.example.editor.metamodel.web.MetamodelHttpTestEnvironment;
import com.example.editor.textual.definition.TextualEditorDefinitionModel;
import com.example.editor.textual.definition.TextualEditorFeature;
import com.example.editor.textual.definition.TextualRuntimeMode;
import com.example.editor.textual.interaction.TextualInteractionFacade;
import com.example.editor.textual.interaction.TextualRenameRequest;
import com.example.editor.textual.runtime.CreateTextualModelSessionRequest;
import com.example.editor.textual.runtime.ParseTextualModelRequest;
import com.example.editor.textual.runtime.TextualModelSessionService;
import com.example.editor.textual.source.TextualModelSourceDocument;
import tools.jackson.databind.json.JsonMapper;

/**
 * Ciclo de vida real de una sesion de modelo textual sobre una gramatica Langium real compilada y activada
 * (contexto completo y HTTP real; todos los directorios en un directorio temporal). Etapas certificadas:
 * x_grammar_to_text_model.an / .parse / .sv, grammar_author_textmodel.pa / .sv, grammar_textual_editing.pa / .sv,
 * grammar_diagnostics_actions.d / .ra y grammar_language_service.a / .rn / .fm / .er. Las posiciones, textos y
 * revisiones esperados son literales del test; el reemplazo de la correccion rapida se aplica con un aplicador propio.
 */
@SpringBootTest
@AutoConfigureMockMvc
class TextualModelSessionRealLifecycleIntegrationTests {

    @TempDir
    static Path root;

    @DynamicPropertySource
    static void directories(DynamicPropertyRegistry registry) {
        MetamodelHttpTestEnvironment.register(registry, root);
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JsonMapper mapper;
    @Autowired
    private TextualInteractionFacade interactions;
    @Autowired
    private TextualModelSessionService sessions;

    private static final String GRAMMAR_SOURCE = "grammar Stable\nentry Model: nodes+=Node*;\nNode: 'node' name=ID;\nhidden terminal WS: /\\s+/;\nterminal ID: /[_a-zA-Z][\\w_]*/;\n";
    private static final String VALID_TEXT = "node alpha  \nnode beta\n";
    private static final String BROKEN_TEXT = "node alpha  \nnode 123\n";
    private static final String FIXED_TEXT = "node alpha  \nnode valor\n";

    private String body(Object value) throws Exception {
        return mapper.writeValueAsString(value);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> read(ResultActions result) throws Exception {
        return mapper.readValue(result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8), Map.class);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> readList(ResultActions result) throws Exception {
        return mapper.readValue(result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8), List.class);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> list(Map<String, Object> source, String key) {
        return (List<Map<String, Object>>) source.get(key);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Map<String, Object> source, String key) {
        return (Map<String, Object>) source.get(key);
    }

    private static List<String> elementIds(Map<String, Object> session, String modelKey) {
        return list(map(session, modelKey), "elements").stream().map(element -> (String) element.get("id")).collect(Collectors.toList());
    }

    private static long number(Map<String, Object> source, String key) {
        return ((Number) source.get(key)).longValue();
    }

    private static int offset(String text, int line, int column) {
        int offset = 0;
        for (int current = 0; current < line; current++) offset = text.indexOf('\n', offset) + 1;
        return offset + column;
    }

    /** Crea gramatica, la compila y activa, define el editor textual y abre la sesion con el texto valido. */
    private Map<String, Object> openSession(String key) throws Exception {
        String grammarId = "tx-grammar-" + key, editorId = "tx-editor-" + key, sessionId = "tx-session-" + key;
        GrammarSourceDocument source = new GrammarSourceDocument("main", "main.langium", "langium", GRAMMAR_SOURCE, "", true, false);
        GrammarDefinitionModel grammar = new GrammarDefinitionModel("1.0", grammarId, grammarId, GrammarTechnology.LANGIUM,
                GrammarAuthoringMode.GRAMMAR_FIRST, "", "langium-4.4-browser", "main", "Model", List.of(source),
                GrammarSemanticBindingMap.empty(), "", Map.of(), Map.of());
        mockMvc.perform(post("/api/grammars").contentType(MediaType.APPLICATION_JSON).content(body(grammar))).andExpect(status().is2xxSuccessful());
        Map<String, Object> snapshot = read(mockMvc.perform(post("/api/grammars/" + grammarId + "/compile").contentType(MediaType.APPLICATION_JSON)
                .content(body(new GrammarCompilationRequest("", grammarId, 1, List.of(source), "langium-4.4-browser", "", true, Map.of()))))
                .andExpect(status().isOk()));
        assertThat(snapshot.get("status")).isEqualTo("SUCCESS");

        TextualEditorDefinitionModel editor = new TextualEditorDefinitionModel("1.0", editorId, editorId, grammarId, "", "", List.of(".ex1"), "Model",
                TextualRuntimeMode.AUTO, List.of(TextualEditorFeature.DIAGNOSTICS, TextualEditorFeature.QUICK_FIXES, TextualEditorFeature.OUTLINE),
                List.of(), List.of(), List.of(), List.of(), List.of(), Map.of());
        mockMvc.perform(post("/api/textual-editor-definitions").contentType(MediaType.APPLICATION_JSON).content(body(editor))).andExpect(status().is2xxSuccessful());

        CreateTextualModelSessionRequest create = new CreateTextualModelSessionRequest(editorId, sessionId,
                List.of(new TextualModelSourceDocument("main", "model.ex1", VALID_TEXT, 0)));
        return read(mockMvc.perform(post("/api/textual-model-sessions").contentType(MediaType.APPLICATION_JSON).content(body(create))).andExpect(status().isOk()));
    }

    private ResultActions parse(String sessionId, long revision, String text) throws Exception {
        ParseTextualModelRequest request = new ParseTextualModelRequest(revision, List.of(new TextualModelSourceDocument("main", "model.ex1", text, revision)));
        return mockMvc.perform(post("/api/textual-model-sessions/" + sessionId + "/parse").contentType(MediaType.APPLICATION_JSON).content(body(request)));
    }

    private Map<String, Object> find(String sessionId) throws Exception {
        return read(mockMvc.perform(get("/api/textual-model-sessions/" + sessionId)).andExpect(status().isOk()));
    }

    @Test
    @DisplayName("x_grammar_to_text_model.an: la recursion por la izquierda bloquea el analisis de la gramatica y su ausencia no")
    void leftRecursionBlocksTheGrammarAnalysisAndItsAbsenceDoesNot() throws Exception {
        for (String[] candidate : new String[][] {
                { "tx-an-left", "grammar Calc\nentry Model: expression=Expression;\nExpression: Expression '+' INT | INT;\n", "true" },
                { "tx-an-plain", GRAMMAR_SOURCE, "false" } }) {
            GrammarSourceDocument document = new GrammarSourceDocument("main", "main.langium", "langium", candidate[1], "", true, false);
            GrammarDefinitionModel grammar = new GrammarDefinitionModel("1.0", candidate[0], candidate[0], GrammarTechnology.LANGIUM,
                    GrammarAuthoringMode.GRAMMAR_FIRST, "", "langium-4.4-browser", "main", "Model", List.of(document),
                    GrammarSemanticBindingMap.empty(), "", Map.of(), Map.of());
            GrammarAnalysisRequest analysis = new GrammarAnalysisRequest(candidate[0], 1, GrammarAnalysisMode.NORMAL, List.of("main"), List.of(document), "langium-4.4-browser");
            Map<String, Object> result = read(mockMvc.perform(post("/api/grammars/analyze-draft").contentType(MediaType.APPLICATION_JSON)
                    .content(body(new GrammarDraftAnalysisRequest(grammar, analysis)))).andExpect(status().isOk()));
            boolean blocked = list(result, "diagnostics").stream().anyMatch(item -> "ERROR".equals(item.get("severity")));
            assertThat(blocked).isEqualTo(Boolean.parseBoolean(candidate[2]));
        }
    }

    @Test
    @DisplayName("x_grammar_to_text_model.parse + grammar_diagnostics_actions.d/.ra + grammar_language_service.er: un borrador roto conserva el ultimo modelo valido y la correccion lo repara")
    void aBrokenDraftKeepsTheLastValidModelAndTheQuickFixReplacementRepairsIt() throws Exception {
        Map<String, Object> created = openSession("broken");
        String id = "tx-session-broken";

        assertThat(number(created, "draftRevision")).isZero();
        assertThat(list(created, "diagnostics")).isEmpty();
        assertThat(elementIds(created, "lastValidSemanticModel")).containsExactly("alpha", "beta");
        assertThat(list(created, "lastValidSourceMap")).isNotEmpty();

        Map<String, Object> broken = read(parse(id, 1, BROKEN_TEXT).andExpect(status().isOk()));
        assertThat(number(broken, "draftRevision")).isEqualTo(1);
        assertThat(number(broken, "lastParsedRevision")).isEqualTo(1);
        assertThat(elementIds(broken, "lastValidSemanticModel")).containsExactly("alpha", "beta");
        assertThat(map(broken, "metadata")).containsEntry("draftValid", "false").containsEntry("lastKnownGood", "revision:0");
        Map<String, Object> diagnostic = list(broken, "diagnostics").stream().filter(item -> "ERROR".equals(item.get("severity"))).findFirst().orElseThrow();
        assertThat(diagnostic.get("code")).isEqualTo("TEXTUAL_GRAMMAR_MISMATCH");
        Map<String, Object> start = map(map(diagnostic, "range"), "start"), end = map(map(diagnostic, "range"), "end");
        assertThat(number(start, "line")).isEqualTo(1);
        assertThat(number(start, "column")).isEqualTo(5);
        assertThat(number(end, "column")).isEqualTo(8);

        // Las consultas de interaccion se rechazan mientras el borrador actual no sea el ultimo valido.
        assertThatThrownBy(() -> interactions.symbols(id, "main")).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("current draft to be the last-known-good revision");

        // Se aplica el reemplazo propuesto (aplicador propio) y se reanaliza con la revision siguiente.
        @SuppressWarnings("unchecked")
        Map<String, String> details = (Map<String, String>) diagnostic.get("metadata");
        assertThat(details).containsEntry("quickFixKind", "REPLACE").containsEntry("quickFixReplacement", "valor");
        String repaired = BROKEN_TEXT.substring(0, offset(BROKEN_TEXT, 1, 5)) + details.get("quickFixReplacement") + BROKEN_TEXT.substring(offset(BROKEN_TEXT, 1, 8));
        assertThat(repaired).isEqualTo(FIXED_TEXT);

        Map<String, Object> fixed = read(parse(id, 2, repaired).andExpect(status().isOk()));
        assertThat(list(fixed, "diagnostics")).isEmpty();
        assertThat(map(fixed, "metadata")).containsEntry("draftValid", "true").containsEntry("lastKnownGood", "revision:2");
        assertThat(elementIds(fixed, "lastValidSemanticModel")).containsExactly("alpha", "valor");
    }

    @Test
    @DisplayName("x_grammar_to_text_model.parse + grammar_textual_editing.pa: se rechaza una revision antigua y la misma revision con otro contenido; la misma revision con el mismo contenido es idempotente")
    void staleRevisionsAndConflictingSameRevisionAreRejected() throws Exception {
        openSession("revisions");
        String id = "tx-session-revisions";
        read(parse(id, 3, FIXED_TEXT).andExpect(status().isOk()));

        parse(id, 2, VALID_TEXT).andExpect(status().isBadRequest());
        parse(id, 3, BROKEN_TEXT).andExpect(status().isBadRequest());
        Map<String, Object> idempotent = read(parse(id, 3, FIXED_TEXT).andExpect(status().isOk()));

        assertThat(number(idempotent, "draftRevision")).isEqualTo(3);
        Map<String, Object> reread = find(id);
        assertThat(number(reread, "draftRevision")).isEqualTo(3);
        assertThat(elementIds(reread, "lastValidSemanticModel")).containsExactly("alpha", "valor");
        assertThat(list(reread, "documents").get(0).get("content")).isEqualTo(FIXED_TEXT);
    }

    @Test
    @DisplayName("grammar_language_service.a/.rn/.fm: con un borrador valido las capacidades, completado, simbolos, renombrado (solo plan) y formato responden sin persistir nada")
    void languageServiceAnswersOnAValidDraftWithoutPersistingAnything() throws Exception {
        openSession("language");
        String id = "tx-session-language";
        String base = "/api/textual-model-sessions/" + id + "/interactions/";

        List<Map<String, Object>> capabilities = readList(mockMvc.perform(get(base + "capabilities")).andExpect(status().isOk()));
        assertThat(capabilities).isNotEmpty();
        assertThat(capabilities).allSatisfy(capability -> {
            assertThat(capability.get("mode")).isEqualTo("HYDRA_NEUTRAL");
            assertThat(capability.get("available")).isEqualTo(true);
        });

        List<Map<String, Object>> completion = readList(mockMvc.perform(get(base + "completion").param("documentId", "main")
                .param("line", "0").param("column", "0").param("prefix", "al")).andExpect(status().isOk()));
        assertThat(completion).extracting(item -> item.get("label")).contains("alpha");

        List<Map<String, Object>> symbols = readList(mockMvc.perform(get(base + "symbols").param("documentId", "main")).andExpect(status().isOk()));
        assertThat(symbols).extracting(item -> item.get("semanticId")).containsExactly("alpha", "beta");
        assertThat(symbols).allSatisfy(symbol -> assertThat(symbol.get("kind")).isEqualTo("MODEL_ELEMENT"));

        Map<String, Object> rename = read(mockMvc.perform(post(base + "rename/preview").contentType(MediaType.APPLICATION_JSON)
                .content(body(new TextualRenameRequest("main", 0, 6, "gamma")))).andExpect(status().isOk()));
        assertThat(rename.get("safe")).isEqualTo(true);
        assertThat(rename.get("oldId")).isEqualTo("alpha");
        assertThat(list(rename, "edits")).hasSize(1);
        assertThat(list(rename, "edits").get(0).get("replacement")).isEqualTo("gamma");
        assertThat(number(map(map(list(rename, "edits").get(0), "range"), "start"), "column")).isEqualTo(5);
        assertThat(number(map(map(list(rename, "edits").get(0), "range"), "end"), "column")).isEqualTo(10);

        Map<String, Object> clash = read(mockMvc.perform(post(base + "rename/preview").contentType(MediaType.APPLICATION_JSON)
                .content(body(new TextualRenameRequest("main", 0, 6, "beta")))).andExpect(status().isOk()));
        assertThat(clash.get("safe")).isEqualTo(false);
        assertThat(list(clash, "conflicts")).isNotEmpty();

        List<Map<String, Object>> format = readList(mockMvc.perform(post(base + "format").param("documentId", "main")).andExpect(status().isOk()));
        assertThat(format).hasSize(1);
        assertThat(format.get(0).get("replacement")).isEqualTo("node alpha\nnode beta\n");

        Map<String, Object> reread = find(id);
        assertThat(number(reread, "draftRevision")).isZero();
        assertThat(list(reread, "documents").get(0).get("content")).isEqualTo(VALID_TEXT);
    }

    @Test
    @DisplayName("x_grammar_to_text_model.sv + grammar_author_textmodel.sv + grammar_textual_editing.sv: guardar promueve el modelo valido analizado y un borrador roto no se puede guardar")
    void savePromotesTheAnalyzedValidModelAndRejectsABrokenDraft() throws Exception {
        openSession("save");
        String id = "tx-session-save";

        Map<String, Object> saved = read(mockMvc.perform(post("/api/textual-model-sessions/" + id + "/save")).andExpect(status().isOk()));
        assertThat(number(saved, "lastCommittedRevision")).isEqualTo(number(saved, "draftRevision"));
        assertThat(elementIds(saved, "lastCommittedSemanticModel")).containsExactly("alpha", "beta");
        assertThat(map(saved, "metadata")).containsEntry("savedRevision", "0");

        read(parse(id, 1, BROKEN_TEXT).andExpect(status().isOk()));
        assertThatThrownBy(() -> sessions.save(id, null)).isInstanceOf(IllegalStateException.class)
                .hasMessage("Only the current valid textual draft can be promoted");

        Map<String, Object> reread = find(id);
        assertThat(number(reread, "draftRevision")).isEqualTo(1);
        assertThat(number(reread, "lastCommittedRevision")).isZero();
        assertThat(elementIds(reread, "lastCommittedSemanticModel")).containsExactly("alpha", "beta");
    }
}
