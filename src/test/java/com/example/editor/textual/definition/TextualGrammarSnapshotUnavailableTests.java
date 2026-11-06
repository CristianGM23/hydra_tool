package com.example.editor.textual.definition;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import com.example.editor.grammar.application.GrammarCompilationService;
import com.example.editor.web.ApiExceptionHandler;

/** Abrir un editor textual cuya gramática no tiene compilación correcta activa es un estado resoluble por el usuario (409), no un 500. */
class TextualGrammarSnapshotUnavailableTests {
    @Test void missingActiveSnapshotIsAConflictWithAnActionableMessage() {
        GrammarCompilationService compilations=mock(GrammarCompilationService.class);
        when(compilations.active("never-compiled")).thenReturn(Optional.empty());
        TextualEditorDefinitionService service=new TextualEditorDefinitionService(null,null,compilations,null,null,null,null,null,null);
        TextualEditorDefinitionModel definition=new TextualEditorDefinitionModel("1.0","editor","editor","never-compiled","","",List.of(),List.of(),List.of(),
                List.of(),List.of(),List.of(),Map.of());

        assertThatThrownBy(() -> service.resolveSnapshot(definition))
                .isInstanceOf(TextualGrammarSnapshotUnavailableException.class)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("never-compiled");

        Throwable thrown=catchThrowable(() -> service.resolveSnapshot(definition));
        var response=new ApiExceptionHandler().handleTextualGrammarSnapshotUnavailable((TextualGrammarSnapshotUnavailableException)thrown);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }
}
