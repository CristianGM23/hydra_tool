package com.example.editor.textual;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.example.editor.grammar.core.GrammarSeverity;
import com.example.editor.grammar.core.TextRange;
import com.example.editor.textual.runtime.TextualModelDiagnostic;
import com.example.editor.textual.runtime.TextualModelSession;
import com.example.editor.textual.source.TextualModelSourceDocument;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/** Regresión: la sesión textual servida al navegador debe llevar el indicador «draftValid» que la pantalla lee. */
class TextualModelSessionDraftValidJsonTests {

    private final ObjectMapper mapper = JsonMapper.builder().build();

    private static TextualModelDiagnostic diagnostic(GrammarSeverity severity) {
        return new TextualModelDiagnostic("TEST", severity, "t", "m", "c", "s", "doc", new TextRange(null, null), Map.of());
    }

    private static TextualModelSession session(List<TextualModelDiagnostic> diagnostics) {
        return new TextualModelSession("s1", "editor", "grammar", "snap",
                List.of(new TextualModelSourceDocument("d1", "model.ex1", "node a", 1)), 1, 1, "hash",
                null, List.of(), null, 0, diagnostics, null, Map.of());
    }

    @Test
    @DisplayName("una sesión sin errores se serializa con draftValid verdadero")
    void serializesDraftValidTrueWithoutErrors() throws Exception {
        JsonNode json = mapper.readTree(mapper.writeValueAsString(session(List.of(diagnostic(GrammarSeverity.WARNING)))));
        assertThat(json.has("draftValid")).isTrue();
        assertThat(json.get("draftValid").booleanValue()).isTrue();
    }

    @Test
    @DisplayName("una sesión con un diagnóstico de error se serializa con draftValid falso")
    void serializesDraftValidFalseWithAnError() throws Exception {
        JsonNode json = mapper.readTree(mapper.writeValueAsString(session(List.of(diagnostic(GrammarSeverity.ERROR)))));
        assertThat(json.has("draftValid")).isTrue();
        assertThat(json.get("draftValid").booleanValue()).isFalse();
    }

    @Test
    @DisplayName("el JSON con draftValid se vuelve a leer como sesión y el indicador se recalcula, no se almacena")
    void roundTripIgnoresStoredDraftValid() throws Exception {
        String json = mapper.writeValueAsString(session(List.of(diagnostic(GrammarSeverity.ERROR))));
        TextualModelSession read = mapper.readValue(json, TextualModelSession.class);
        assertThat(read.draftValid()).isFalse();
        String forged = json.replace("\"draftValid\":false", "\"draftValid\":true");
        assertThat(mapper.readValue(forged, TextualModelSession.class).draftValid()).isFalse();
    }
}
