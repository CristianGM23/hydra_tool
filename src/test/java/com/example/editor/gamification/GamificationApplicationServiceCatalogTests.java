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
        when(definitions.find("broken")).thenThrow(new CanonicalDocumentStoreException(Reason.STORAGE_ERROR,
                "Could not parse document: C:\\workspace\\data\\gamification\\broken.gamification-definitions.json"));
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
        assertThat(result.diagnostics()).singleElement().asString()
                .contains("Skipped invalid gamification document broken")
                .contains("Could not parse document")
                .contains("broken.gamification-definitions.json");
    }
}
