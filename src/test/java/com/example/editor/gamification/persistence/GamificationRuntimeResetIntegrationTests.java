package com.example.editor.gamification.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.editor.event.CanonicalEvent;
import com.example.editor.event.EventActor;
import com.example.editor.event.EventActorKind;
import com.example.editor.event.EventContext;
import com.example.editor.event.EventContextKind;
import com.example.editor.event.EventSource;
import com.example.editor.event.bus.InMemoryCanonicalEventBus;
import com.example.editor.gamification.GamificationApplicationService;
import com.example.editor.gamification.GamificationBindingModel;
import com.example.editor.gamification.GamificationCheckReport;
import com.example.editor.gamification.GamificationCorruptDocumentRecovery;
import com.example.editor.gamification.GamificationDefinitionModel;
// NOTE: GamificationCorruptDocumentRecovery.disabled() is package-private to com.example.editor.gamification;
// this test lives in the .persistence sub-package, so it uses the public @Autowired constructor instead,
// which is harmless here since no document ever fails to parse in this scenario.
import com.example.editor.gamification.GamificationEffectKind;
import com.example.editor.gamification.GamificationLoadResult;
import com.example.editor.gamification.GamificationProperties;
import com.example.editor.gamification.GamificationRepeatPolicy;
import com.example.editor.gamification.GamificationRule;
import com.example.editor.gamification.PointTypeDefinition;
import com.example.editor.gamification.runtime.GamificationProcessingResult;
import com.example.editor.gamification.runtime.GamificationRuntimeService;
import tools.jackson.databind.json.JsonMapper;

/**
 * Evidencia FUERTE real, sin mocks, del reinicio del historial de gamificación sobre disco real:
 * cubre directamente la etapa "do" (Borrar estado e historial) de gamification_author_ledger.
 * A diferencia de GamificationPenaltyRuntimeTests/GamificationCompensationRuntimeTests (repositorio
 * en memoria propio), esta prueba usa el JsonGamificationRuntimeRepository real respaldado por
 * ficheros reales en @TempDir, para demostrar que el reinicio borra realmente los documentos del
 * disco y no solo una cache en memoria.
 */
class GamificationRuntimeResetIntegrationTests {

    @Test
    void resettingAfterARealAwardDeletesBothRealFilesFromDiskAndClearsTheRuntimeState(@TempDir Path temporary) {
        GamificationProperties properties = new GamificationProperties();
        properties.setDirectory(temporary);
        JsonGamificationRuntimeRepository repository = new JsonGamificationRuntimeRepository(properties,
                JsonMapper.builder().findAndAddModules().build());
        GamificationDefinitionModel definitions = new GamificationDefinitionModel("1.0", "gamification-definitions",
                "metamodel", "Gamification", List.of(new PointTypeDefinition("points:quality", "Quality", "", 0)),
                List.of(), List.of(), List.of(), List.of(), Map.of());
        GamificationRule award = new GamificationRule("rule:award", "rule:award", "custom.award", "", "EQUALS", null,
                "points:quality", 10, "", "", "", GamificationRepeatPolicy.ONCE_PER_EVENT, 0, 100, 100,
                GamificationEffectKind.AWARD, true);
        GamificationBindingModel bindings = new GamificationBindingModel("1.0", "gamification-bindings", "metamodel",
                definitions.id(), List.of(award), Map.of());
        GamificationLoadResult configuration = new GamificationLoadResult(definitions, "definition-etag", bindings,
                "binding-etag", new GamificationCheckReport(definitions.id(), bindings.id(), List.of()));
        GamificationApplicationService application = new GamificationApplicationService(null, null, null, null, null,
                new GamificationCorruptDocumentRecovery(properties)) {
            @Override public GamificationLoadResult find(String metamodelId) { return configuration; }
        };
        GamificationRuntimeService runtime = new GamificationRuntimeService(application, repository, properties,
                new InMemoryCanonicalEventBus());

        GamificationProcessingResult awardResult = runtime.process("metamodel", event("event:award", "custom.award"));
        assertThat(awardResult.granted()).hasSize(1);
        assertThat(repository.findState("metamodel")).isPresent();
        assertThat(repository.findLedger("metamodel")).isPresent();
        Path stateFile = temporary.resolve("metamodel" + properties.getStateFileSuffix());
        Path ledgerFile = temporary.resolve("metamodel" + properties.getLedgerFileSuffix());
        assertThat(Files.exists(stateFile)).isTrue();
        assertThat(Files.exists(ledgerFile)).isTrue();

        runtime.reset("metamodel");

        // "do": el reinicio borra de verdad ambos ficheros reales en disco, no solo una cache.
        assertThat(Files.exists(stateFile)).isFalse();
        assertThat(Files.exists(ledgerFile)).isFalse();
        assertThat(repository.findState("metamodel")).isEmpty();
        assertThat(repository.findLedger("metamodel")).isEmpty();
        // El runtime refleja el reinicio real: ni el saldo del actor ni el libro sobreviven.
        assertThat(runtime.state("metamodel").state().actors()).isEmpty();
        assertThat(runtime.state("metamodel").ledger().entries()).isEmpty();
        // El reinicio tambien invalida la cache de avisos para ese metamodelo.
        assertThat(runtime.feedback("metamodel", "actor:one", 100)).isEmpty();
    }

    private static CanonicalEvent event(String id, String type) {
        return new CanonicalEvent("1.0", id, type, "1.0", Instant.parse("2026-07-28T10:00:00Z"),
                new EventSource("test", "gamification", "", "1.0"),
                new EventContext(EventContextKind.GAMIFICATION, "", "", "metamodel", "model", "", "session:one"),
                new EventActor("actor:one", EventActorKind.USER, "Actor"), null, "transaction:one", "correlation:one", "",
                Map.of(), Map.of());
    }
}
