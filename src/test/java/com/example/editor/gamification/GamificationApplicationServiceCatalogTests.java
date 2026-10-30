package com.example.editor.gamification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import com.example.editor.gamification.persistence.GamificationBindingRepository;
import com.example.editor.gamification.persistence.GamificationDefinitionRepository;
import com.example.editor.validation.persistence.CanonicalDocumentStoreException;
import com.example.editor.validation.persistence.CanonicalDocumentStoreException.Reason;
import com.example.editor.validation.persistence.StoredCanonicalDocument;

class GamificationApplicationServiceCatalogTests {
    @Test
    void skipsOneMalformedDefinitionWithoutMakingReadinessUnavailable() {
        GamificationDefinitionRepository definitions = mock(GamificationDefinitionRepository.class);
        GamificationBindingRepository bindings = mock(GamificationBindingRepository.class);
        when(definitions.listIds()).thenReturn(List.of("broken", "healthy"));
        // exactly what AtomicJsonDocumentStore throws for unparseable bytes: the message names the server path and the error is flagged malformed
        when(definitions.find("broken")).thenThrow(new CanonicalDocumentStoreException(Reason.STORAGE_ERROR,
                "Could not parse document: C:\\workspace\\data\\gamification\\broken.gamification-definitions.json", null, true));
        GamificationDefinitionModel definition = new GamificationDefinitionModel(null, "healthy-game", "healthy",
                "Healthy game", List.of(), List.of(), List.of(), List.of(), List.of(), Map.of());
        GamificationBindingModel binding = new GamificationBindingModel(null, "healthy-bindings", "healthy",
                "healthy-game", List.of(), Map.of());
        when(definitions.find("healthy")).thenReturn(Optional.of(new StoredCanonicalDocument<>(definition,
                "definition-etag", Path.of("healthy.gamification-definitions.json"))));
        when(bindings.find("healthy")).thenReturn(Optional.of(new StoredCanonicalDocument<>(binding,
                "binding-etag", Path.of("healthy.gamification-bindings.json"))));
        GamificationApplicationService service = new GamificationApplicationService(definitions, bindings, null, null, null, GamificationCorruptDocumentRecovery.disabled());

        GamificationCatalogResult result = service.listWithDiagnostics();

        assertThat(result.summaries()).singleElement().satisfies(summary -> {
            assertThat(summary.metamodelId()).isEqualTo("healthy");
            assertThat(summary.definitionModelId()).isEqualTo("healthy-game");
        });
        // Until the jar 15 this diagnostic had to repeat the store message, server path included; Home readiness shows it to the user,
        // and the user required on 30/10 that diagnostics never disclose a path, even inside a 200. It names the metamodel and the problem.
        assertThat(result.diagnostics()).singleElement().asString()
                .contains("metamodel broken")
                .contains("definitions are malformed")
                .contains("left unchanged")
                .doesNotContain("C:\\workspace")
                .doesNotContain("broken.gamification-definitions.json")
                .doesNotContain("Could not parse document");
    }
}
