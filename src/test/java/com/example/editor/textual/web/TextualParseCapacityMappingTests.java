package com.example.editor.textual.web;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.example.editor.grammar.application.GrammarCompilationService;
import com.example.editor.grammar.core.CompilationStatus;
import com.example.editor.grammar.core.GeneratedGrammarProject;
import com.example.editor.grammar.core.GrammarRuntimeDescriptor;
import com.example.editor.grammar.core.GrammarSemanticBindingMap;
import com.example.editor.grammar.core.GrammarTechnology;
import com.example.editor.grammar.runtime.GrammarCompilationSnapshot;
import com.example.editor.textual.TextualModelProperties;
import com.example.editor.textual.definition.TextualEditorDefinitionService;
import com.example.editor.textual.interaction.TextualInteractionFacade;
import com.example.editor.textual.persistence.TextualModelSessionRepository;
import com.example.editor.textual.runtime.TextualModelSession;
import com.example.editor.textual.runtime.TextualModelSessionService;
import com.example.editor.textual.runtime.TextualQuickFixRuntimeService;
import com.example.editor.textual.runtime.TextualSharedLayerRuntimeService;
import com.example.editor.textual.source.TextualModelSourceDocument;
import com.example.editor.validation.persistence.StoredCanonicalDocument;
import com.example.editor.web.ApiExceptionHandler;

/**
 * When every textual parse permit is taken (concurrent parses of large documents, battery scenario CONC part D), a further parse is refused
 * as a temporary unavailability the client can retry (503 + Retry-After), never as an internal server error.
 */
class TextualParseCapacityMappingTests {

    @Test
    void aParseRefusedForLackOfCapacityIsAServiceUnavailableWithRetryAfter() throws Exception {
        TextualModelProperties properties = new TextualModelProperties();
        properties.setMaximumConcurrentParses(1);
        properties.setPermitAcquireTimeoutMillis(20);
        TextualModelSourceDocument document = new TextualModelSourceDocument("model", "model.xtext.model", "node n1 {}", 7);
        TextualModelSession session = new TextualModelSession("session", "editor", "grammar", "snapshot", List.of(document), 7, 7, "hash", null, List.of(), null, 0, List.of(),
                Instant.now(), Map.of("lastKnownGood", "revision:7"));
        TextualModelSessionRepository repository = mock(TextualModelSessionRepository.class);
        when(repository.find("session")).thenReturn(Optional.of(new StoredCanonicalDocument<>(session, "\"etag-1\"", Path.of("session.json"))));
        GrammarCompilationService compilations = mock(GrammarCompilationService.class);
        when(compilations.find("snapshot")).thenReturn(snapshot());
        TextualModelSessionService service = new TextualModelSessionService(repository, mock(TextualEditorDefinitionService.class), properties, compilations,
                mock(TextualSharedLayerRuntimeService.class));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new TextualModelSessionController(service, mock(TextualQuickFixRuntimeService.class), mock(TextualInteractionFacade.class)))
                .setControllerAdvice(new ApiExceptionHandler()).build();

        // another parse holds the only permit for the whole request
        java.lang.reflect.Field slots = TextualModelSessionService.class.getDeclaredField("parseSlots");
        slots.setAccessible(true);
        try (var held = ((com.example.editor.concurrent.BoundedPermitPool) slots.get(service)).tryAcquire(Duration.ofSeconds(1))) {
            mvc.perform(post("/api/textual-model-sessions/session/parse").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"draftRevision\":8,\"documents\":[{\"id\":\"model\",\"relativePath\":\"model.xtext.model\",\"content\":\"node n2 {}\",\"revision\":8}]}"))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(header().exists("Retry-After"))
                    .andExpect(jsonPath("$.message").value("Textual parse capacity is busy; retry later"));
        }
    }

    private static GrammarCompilationSnapshot snapshot() {
        return new GrammarCompilationSnapshot("snapshot", "grammar", 1, "source-hash", GrammarTechnology.XTEXT, "provider", "toolchain", CompilationStatus.SUCCESS, List.of(),
                GrammarSemanticBindingMap.empty(), new GrammarRuntimeDescriptor("", "", List.of(), "", "", Map.of()), new GeneratedGrammarProject("", Map.of(), Map.of()), Map.of(),
                Instant.now(), Map.of("immutable", "true"));
    }
}
