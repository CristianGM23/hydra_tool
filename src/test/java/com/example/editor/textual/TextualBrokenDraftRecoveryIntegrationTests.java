package com.example.editor.textual;

import static org.assertj.core.api.Assertions.assertThat;
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

import com.example.editor.grammar.core.GrammarCompilationRequest;
import com.example.editor.grammar.core.GrammarAuthoringMode;
import com.example.editor.grammar.core.GrammarDefinitionModel;
import com.example.editor.grammar.core.GrammarSemanticBindingMap;
import com.example.editor.grammar.core.GrammarSourceDocument;
import com.example.editor.grammar.core.GrammarTechnology;
import com.example.editor.metamodel.web.MetamodelHttpTestEnvironment;
import com.example.editor.textual.definition.TextualEditorDefinitionModel;
import com.example.editor.textual.definition.TextualEditorFeature;
import com.example.editor.textual.definition.TextualRuntimeMode;
import com.example.editor.textual.runtime.CreateTextualModelSessionRequest;
import com.example.editor.textual.runtime.ParseTextualModelRequest;
import com.example.editor.textual.source.TextualModelSourceDocument;
import tools.jackson.databind.json.JsonMapper;

/**
 * Regresión de los defectos #15 y #14 de HALLAZGOS_PRODUCCION sobre una gramática Langium real compilada y activada
 * (contexto completo y HTTP real). #15: la corrección rápida de inserción al final del texto proponía «valor» pegado al
 * último token. #14: consultar o guardar un borrador roto respondía 500 INTERNAL_ERROR en vez de un error de cliente.
 * El texto reparado se calcula con un aplicador propio y se vuelve a analizar por HTTP; los códigos HTTP son literales.
 */
@SpringBootTest
@AutoConfigureMockMvc
class TextualBrokenDraftRecoveryIntegrationTests {

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

    private static final String GRAMMAR_SOURCE = "grammar Stable\nentry Model: nodes+=Node*;\nNode: 'node' name=ID;\n";
    private static final String VALID_TEXT = "node alpha  \nnode beta\n";
    private static final String TRUNCATED_TEXT = "node alpha\nnode";
    private static final String BROKEN_TEXT = "node alpha  \nnode 123\n";

    private String body(Object value) throws Exception {
        return mapper.writeValueAsString(value);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> read(ResultActions result) throws Exception {
        return mapper.readValue(result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8), Map.class);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> list(Map<String, Object> source, String key) {
        return (List<Map<String, Object>>) source.get(key);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Map<String, Object> source, String key) {
        return (Map<String, Object>) source.get(key);
    }

    private static int offset(String text, Map<String, Object> position) {
        int line = ((Number) position.get("line")).intValue(), column = ((Number) position.get("column")).intValue();
        int offset = 0;
        for (int current = 0; current < line; current++) offset = text.indexOf('\n', offset) + 1;
        return offset + column;
    }

    /** Crea gramatica, la compila y activa, define el editor textual y abre la sesion con el texto valido. */
    private String openSession(String key) throws Exception {
        String grammarId = "tb-grammar-" + key, editorId = "tb-editor-" + key, sessionId = "tb-session-" + key;
        GrammarSourceDocument source = new GrammarSourceDocument("main", "main.langium", "langium", GRAMMAR_SOURCE, "", true, false);
        GrammarDefinitionModel grammar = new GrammarDefinitionModel("1.0", grammarId, grammarId, GrammarTechnology.LANGIUM,
                GrammarAuthoringMode.GRAMMAR_FIRST, "", "langium-4.3-browser", "main", "Model", List.of(source),
                GrammarSemanticBindingMap.empty(), "", Map.of(), Map.of());
        mockMvc.perform(post("/api/grammars").contentType(MediaType.APPLICATION_JSON).content(body(grammar))).andExpect(status().is2xxSuccessful());
        Map<String, Object> snapshot = read(mockMvc.perform(post("/api/grammars/" + grammarId + "/compile").contentType(MediaType.APPLICATION_JSON)
                .content(body(new GrammarCompilationRequest("", grammarId, 1, List.of(source), "langium-4.3-browser", "", true, Map.of()))))
                .andExpect(status().isOk()));
        assertThat(snapshot.get("status")).isEqualTo("SUCCESS");

        TextualEditorDefinitionModel editor = new TextualEditorDefinitionModel("1.0", editorId, editorId, grammarId, "", "", List.of(".ex1"), "Model",
                TextualRuntimeMode.AUTO, List.of(TextualEditorFeature.DIAGNOSTICS, TextualEditorFeature.QUICK_FIXES, TextualEditorFeature.OUTLINE),
                List.of(), List.of(), List.of(), List.of(), List.of(), Map.of());
        mockMvc.perform(post("/api/textual-editor-definitions").contentType(MediaType.APPLICATION_JSON).content(body(editor))).andExpect(status().is2xxSuccessful());

        CreateTextualModelSessionRequest create = new CreateTextualModelSessionRequest(editorId, sessionId,
                List.of(new TextualModelSourceDocument("main", "model.ex1", VALID_TEXT, 0)));
        mockMvc.perform(post("/api/textual-model-sessions").contentType(MediaType.APPLICATION_JSON).content(body(create))).andExpect(status().isOk());
        return sessionId;
    }

    private ResultActions parse(String sessionId, long revision, String text) throws Exception {
        ParseTextualModelRequest request = new ParseTextualModelRequest(revision, List.of(new TextualModelSourceDocument("main", "model.ex1", text, revision)));
        return mockMvc.perform(post("/api/textual-model-sessions/" + sessionId + "/parse").contentType(MediaType.APPLICATION_JSON).content(body(request)));
    }

    @Test
    @DisplayName("grammar_diagnostics_actions.ra: la correccion de insercion al final del texto se separa del ultimo token y el texto reparado vuelve a ser valido")
    void anInsertQuickFixAtTheEndOfTheTextIsSeparatedFromTheLastToken() throws Exception {
        String id = openSession("insert");

        Map<String, Object> truncated = read(parse(id, 1, TRUNCATED_TEXT).andExpect(status().isOk()));
        Map<String, Object> diagnostic = list(truncated, "diagnostics").stream().filter(item -> "ERROR".equals(item.get("severity"))).findFirst().orElseThrow();
        @SuppressWarnings("unchecked")
        Map<String, String> details = (Map<String, String>) diagnostic.get("metadata");
        assertThat(details).containsEntry("quickFixKind", "INSERT");
        assertThat(details.get("quickFixReplacement")).isEqualTo(" valor");

        Map<String, Object> range = map(diagnostic, "range");
        String repaired = TRUNCATED_TEXT.substring(0, offset(TRUNCATED_TEXT, map(range, "start")))
                + details.get("quickFixReplacement") + TRUNCATED_TEXT.substring(offset(TRUNCATED_TEXT, map(range, "end")));
        assertThat(repaired).isEqualTo("node alpha\nnode valor");

        Map<String, Object> fixed = read(parse(id, 2, repaired).andExpect(status().isOk()));
        assertThat(list(fixed, "diagnostics")).isEmpty();
        assertThat(map(fixed, "metadata")).containsEntry("draftValid", "true");
        assertThat(list(map(fixed, "lastValidSemanticModel"), "elements").stream().map(element -> (String) element.get("id")).collect(Collectors.toList()))
                .containsExactly("alpha", "valor");
    }

    @Test
    @DisplayName("grammar_textual_editing.sv + grammar_language_service.er: consultar o guardar un borrador roto es un conflicto de cliente (409), no un error de servidor")
    void queryingOrSavingABrokenDraftIsAClientConflictNotAServerError() throws Exception {
        String id = openSession("conflict");
        parse(id, 1, BROKEN_TEXT).andExpect(status().isOk());

        Map<String, Object> query = read(mockMvc.perform(get("/api/textual-model-sessions/" + id + "/interactions/symbols").param("documentId", "main"))
                .andExpect(status().isConflict()));
        Map<String, Object> save = read(mockMvc.perform(post("/api/textual-model-sessions/" + id + "/save"))
                .andExpect(status().isConflict()));

        assertThat(query.get("code")).isEqualTo("CONFLICT");
        assertThat((String) query.get("message")).contains("last-known-good");
        assertThat(save.get("code")).isEqualTo("CONFLICT");
        assertThat((String) save.get("message")).isEqualTo("Only the current valid textual draft can be promoted");

        // Control positivo: con un borrador valido las mismas operaciones funcionan.
        parse(id, 2, VALID_TEXT).andExpect(status().isOk());
        mockMvc.perform(get("/api/textual-model-sessions/" + id + "/interactions/symbols").param("documentId", "main")).andExpect(status().isOk());
        mockMvc.perform(post("/api/textual-model-sessions/" + id + "/save")).andExpect(status().isOk());
    }
}
