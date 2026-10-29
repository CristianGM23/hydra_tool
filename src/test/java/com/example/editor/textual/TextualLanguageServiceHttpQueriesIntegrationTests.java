package com.example.editor.textual;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

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

import com.example.editor.grammar.core.GrammarAuthoringMode;
import com.example.editor.grammar.core.GrammarCompilationRequest;
import com.example.editor.grammar.core.GrammarDefinitionModel;
import com.example.editor.grammar.core.GrammarSemanticBindingMap;
import com.example.editor.grammar.core.GrammarSourceDocument;
import com.example.editor.grammar.core.GrammarTechnology;
import com.example.editor.metamodel.web.MetamodelHttpTestEnvironment;
import com.example.editor.textual.definition.TextualEditorDefinitionModel;
import com.example.editor.textual.definition.TextualEditorFeature;
import com.example.editor.textual.definition.TextualRuntimeMode;
import com.example.editor.textual.runtime.CreateTextualModelSessionRequest;
import com.example.editor.textual.source.TextualModelSourceDocument;
import tools.jackson.databind.json.JsonMapper;

/**
 * Evidencia real (contexto completo y HTTP, todos los directorios temporales) de las consultas restantes del
 * servicio de lenguaje de una sesion textual: grammar_language_service.q (informacion al pasar el cursor,
 * definicion, referencias, plegado, tokens semanticos, invalidacion y compatibilidad de instantaneas). Las
 * posiciones y los identificadores esperados son literales del texto fuente del test; una sesion de lectura
 * no cambia nada: se relee y el borrador queda como estaba.
 */
@SpringBootTest
@AutoConfigureMockMvc
class TextualLanguageServiceHttpQueriesIntegrationTests {

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

    private static final String GRAMMAR_SOURCE = "grammar Stable\nentry Model: nodes+=Node*;\nNode: 'node' name=ID;\nhidden terminal WS: /\\s+/;\nterminal ID: /[_a-zA-Z][\\w_]*/;\n";
    private static final String VALID_TEXT = "node alpha  \nnode beta\n";
    private static final String SESSION = "tx-session-queries";

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
    private static Map<String, Object> map(Map<String, Object> source, String key) {
        return (Map<String, Object>) source.get(key);
    }

    private static long number(Map<String, Object> source, String key) {
        return ((Number) source.get(key)).longValue();
    }

    private static boolean opened;

    /** Los dos casos solo leen: la sesion se crea una vez y se comparte (el contexto y el directorio son comunes). */
    private synchronized void openSession() throws Exception {
        if (opened) return;
        String grammarId = "tx-grammar-queries", editorId = "tx-editor-queries";
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
        CreateTextualModelSessionRequest create = new CreateTextualModelSessionRequest(editorId, SESSION,
                List.of(new TextualModelSourceDocument("main", "model.ex1", VALID_TEXT, 0)));
        mockMvc.perform(post("/api/textual-model-sessions").contentType(MediaType.APPLICATION_JSON).content(body(create))).andExpect(status().isOk());
        opened = true;
    }

    @Test
    @DisplayName("grammar_language_service.q: hover, definicion, referencias y tokens devuelven las posiciones e identificadores literales del texto")
    void hoverDefinitionReferencesAndTokensAnswerWithTheLiteralPositionsOfTheText() throws Exception {
        openSession();
        String base = "/api/textual-model-sessions/" + SESSION + "/interactions/";

        Map<String, Object> hover = read(mockMvc.perform(get(base + "hover").param("documentId", "main").param("line", "0").param("column", "6")).andExpect(status().isOk()));
        assertThat(hover.get("found")).isEqualTo(true);
        assertThat(hover.get("semanticId")).isEqualTo("alpha");
        assertThat(hover.get("documentId")).isEqualTo("main");
        Map<String, Object> nothing = read(mockMvc.perform(get(base + "hover").param("documentId", "main").param("line", "5").param("column", "0")).andExpect(status().isOk()));
        assertThat(nothing.get("found")).isEqualTo(false);

        Map<String, Object> definition = read(mockMvc.perform(get(base + "definition").param("documentId", "main").param("line", "1").param("column", "6")).andExpect(status().isOk()));
        assertThat(definition.get("semanticId")).isEqualTo("beta");
        assertThat(definition.get("documentId")).isEqualTo("main");
        assertThat(number(map(map(definition, "range"), "start"), "line")).isEqualTo(1);
        // La definicion senala la declaracion completa ("node beta", columnas 0 a 9), no solo el identificador.
        assertThat(number(map(map(definition, "range"), "start"), "column")).isZero();
        assertThat(number(map(map(definition, "range"), "end"), "column")).isGreaterThanOrEqualTo(9);

        List<Map<String, Object>> references = readList(mockMvc.perform(get(base + "references").param("documentId", "main").param("line", "0").param("column", "6")).andExpect(status().isOk()));
        assertThat(references).isNotEmpty();
        assertThat(references).allSatisfy(reference -> {
            assertThat(reference.get("semanticId")).isEqualTo("alpha");
            assertThat(number(map(map(reference, "range"), "start"), "line")).isZero();
        });

        List<Map<String, Object>> tokens = readList(mockMvc.perform(get(base + "semantic-tokens").param("documentId", "main")).andExpect(status().isOk()));
        assertThat(tokens).anySatisfy(token -> {
            assertThat(token.get("semanticId")).isEqualTo("alpha");
            assertThat(number(token, "line")).isZero();
            assertThat(number(token, "column")).isLessThanOrEqualTo(5);
            assertThat(number(token, "column") + number(token, "length")).isGreaterThanOrEqualTo(10);
        });
        assertThat(tokens).anySatisfy(token -> {
            assertThat(token.get("semanticId")).isEqualTo("beta");
            assertThat(number(token, "line")).isEqualTo(1);
            assertThat(number(token, "column")).isLessThanOrEqualTo(5);
            assertThat(number(token, "column") + number(token, "length")).isGreaterThanOrEqualTo(9);
        });
    }

    @Test
    @DisplayName("grammar_language_service.q: plegado, invalidacion y compatibilidad responden y no cambian la sesion")
    void foldingInvalidationAndSnapshotCompatibilityAnswerWithoutChangingTheSession() throws Exception {
        openSession();
        String base = "/api/textual-model-sessions/" + SESSION + "/interactions/";

        List<Map<String, Object>> folding = readList(mockMvc.perform(get(base + "folding").param("documentId", "main")).andExpect(status().isOk()));
        assertThat(folding).allSatisfy(range -> {
            assertThat(number(range, "startLine")).isGreaterThanOrEqualTo(0);
            assertThat(number(range, "endLine")).isGreaterThanOrEqualTo(number(range, "startLine"));
            assertThat(number(range, "endLine")).isLessThanOrEqualTo(2);
        });

        Map<String, Object> plan = read(mockMvc.perform(post(base + "invalidation").contentType(MediaType.APPLICATION_JSON).content(body(List.of("main"))))
                .andExpect(status().isOk()));
        assertThat((List<?>) plan.get("changedDocuments")).hasSize(1);
        assertThat(((List<?>) plan.get("changedDocuments")).get(0)).isEqualTo("main");

        Map<String, Object> same = read(mockMvc.perform(post(base + "snapshot-compatibility").contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("previousSnapshotId", "snapshot:one", "currentSnapshotId", "snapshot:one", "previousGrammarId", "grammar-a",
                        "currentGrammarId", "grammar-a", "previousSemanticContractHash", "hash-a", "currentSemanticContractHash", "hash-a",
                        "previousProvider", "provider-a", "currentProvider", "provider-a")))).andExpect(status().isOk()));
        assertThat(same.get("reusable")).isEqualTo(true);
        assertThat(same.get("reparseRequired")).isEqualTo(false);
        Map<String, Object> different = read(mockMvc.perform(post(base + "snapshot-compatibility").contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("previousSnapshotId", "snapshot:one", "currentSnapshotId", "snapshot:two", "previousGrammarId", "grammar-a",
                        "currentGrammarId", "grammar-b", "previousSemanticContractHash", "hash-a", "currentSemanticContractHash", "hash-b",
                        "previousProvider", "provider-a", "currentProvider", "provider-b")))).andExpect(status().isOk()));
        assertThat(different.get("reusable")).isEqualTo(false);

        Map<String, Object> reread = read(mockMvc.perform(get("/api/textual-model-sessions/" + SESSION)).andExpect(status().isOk()));
        assertThat(number(reread, "draftRevision")).isZero();
        assertThat(((List<?>) reread.get("documents")).size()).isEqualTo(1);
        assertThat(((Map<?, ?>) ((List<?>) reread.get("documents")).get(0)).get("content")).isEqualTo(VALID_TEXT);
    }
}
