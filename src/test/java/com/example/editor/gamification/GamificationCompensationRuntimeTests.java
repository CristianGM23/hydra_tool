package com.example.editor.gamification;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.example.editor.event.CanonicalEvent;
import com.example.editor.event.EventActor;
import com.example.editor.event.EventActorKind;
import com.example.editor.event.EventContext;
import com.example.editor.event.EventContextKind;
import com.example.editor.event.EventSource;
import com.example.editor.event.bus.InMemoryCanonicalEventBus;
import com.example.editor.gamification.persistence.GamificationRuntimeRepository;
import com.example.editor.gamification.runtime.GamificationProcessingResult;
import com.example.editor.gamification.runtime.GamificationRuntimeService;
import com.example.editor.validation.persistence.StoredCanonicalDocument;

/**
 * Evidencia FUERTE real, sin mocks, de la reversion/compensacion de una concesion ya otorgada:
 * cubre directamente las etapas "rev" (¿Es una reversion?) y "comp" (Revertir lo concedido) de
 * gamification_award, y "find"/"any"/"mark"/"neg"/"skip"/"reb"/"pub"/"art" de
 * gamification_compensate.
 * Reutiliza el mismo GamificationRuntimeService real que GamificationPenaltyRuntimeTests, con un
 * repositorio en memoria real (no Mockito) y un bus de eventos real.
 */
class GamificationCompensationRuntimeTests {

    @Test
    void compensatingAPriorAwardMarksItCompensatedCreatesARealReversalAndRebuildsTheBalance() {
        Fixture fixture = fixture(List.of(award(10)));
        GamificationProcessingResult awardResult = fixture.process("event:award", "custom.award", Map.of());
        String awardedEntryId = fixture.repository.ledger.entries().get(0).id();
        assertThat(awardResult.granted()).hasSize(1);
        assertThat(awardResult.state().actors().get("actor:one").points()).containsEntry("points:quality", 10L);

        // "rev"/"find": un evento real cuyo payload compensatesEventId apunta al evento ya
        // concedido se reconoce como una reversion y localiza la concesion activa relacionada.
        GamificationProcessingResult compensationResult = fixture.process("event:compensate", "core.quickfix.undone",
                Map.of("compensatesEventId", "event:award"));

        // "comp"/"mark"/"neg": la entrada original se marca COMPENSATED y se anota una reversal
        // real con el signo contrario de puntos, enlazada a la original, en vez de borrar o
        // reescribir el historial.
        assertThat(compensationResult.compensated()).hasSize(1);
        assertThat(compensationResult.compensated().get(0).pointsDelta()).isEqualTo(-10L);
        assertThat(compensationResult.compensated().get(0).compensationOf()).isEqualTo(awardedEntryId);
        assertThat(fixture.repository.ledger.entries()).filteredOn(entry -> entry.id().equals(awardedEntryId))
                .singleElement().satisfies(entry -> assertThat(entry.status()).isEqualTo(GamificationLedgerStatus.COMPENSATED));
        // "art": la entrada de reversion real, con su propio identificador, queda realmente
        // guardada en el libro real (no solo devuelta en el resultado en memoria) y sigue
        // encontrable en el historial tras el guardado real.
        String reversalEntryId = compensationResult.compensated().get(0).id();
        assertThat(reversalEntryId).isNotEqualTo(awardedEntryId);
        assertThat(fixture.repository.ledger.entries()).extracting(entry -> entry.id())
                .contains(awardedEntryId, reversalEntryId);
        // "reb": el saldo se recalcula desde el libro real; ni la entrada original (ahora
        // COMPENSATED) ni su reversal (enlazada via compensationOf) cuentan para el saldo activo,
        // asi que sin ninguna otra concesion el actor desaparece del estado reconstruido en vez de
        // quedar con un saldo contado dos veces.
        assertThat(compensationResult.state().actors()).doesNotContainKey("actor:one");
        // "pub": se publica un evento real de compensacion tras confirmar el cambio.
        assertThat(fixture.published).contains("core.gamification.award-compensated");
        // Esta reversion no concede ni rechaza nada por si misma: solo compensa.
        assertThat(compensationResult.granted()).isEmpty();
        assertThat(compensationResult.rejected()).isEmpty();
    }

    @Test
    void anUnrelatedCompensationReferenceFindsNothingAndTheEventIsTreatedAsNormal() {
        Fixture fixture = fixture(List.of(award(10)));
        fixture.process("event:award", "custom.award", Map.of());

        // "any": no hay ninguna concesion activa que coincida con ese identificador de evento, asi
        // que no se compensa nada real y el evento se procesa como uno normal (sin reglas que
        // coincidan con este tipo, por lo que tampoco concede ni rechaza nada).
        GamificationProcessingResult result = fixture.process("event:unrelated-undo", "core.quickfix.undone",
                Map.of("compensatesEventId", "event:does-not-exist"));

        assertThat(result.compensated()).isEmpty();
        assertThat(result.granted()).isEmpty();
        assertThat(result.rejected()).isEmpty();
        // La concesion original sigue activa: no se vio afectada por la referencia no relacionada.
        assertThat(fixture.repository.ledger.entries()).extracting(entry -> entry.status())
                .containsExactly(GamificationLedgerStatus.ACTIVE);
    }

    @Test
    void aRecognizedCompensationEventSkipsNormalRuleEvaluationEvenWhenARuleWouldOtherwiseMatch() {
        // Una regla ligada al mismo tipo de evento que el disparador de compensacion: si las reglas
        // normales se evaluasen igualmente, este evento tambien concederia 50 puntos.
        GamificationRule conflicting = new GamificationRule("rule:undo-award", "rule:undo-award",
                "core.quickfix.undone", "", "EQUALS", null, "points:quality", 50, "", "", "",
                GamificationRepeatPolicy.ONCE_PER_EVENT, 0, 100, 100, GamificationEffectKind.AWARD, true);
        Fixture fixture = fixture(List.of(award(10), conflicting));
        fixture.process("event:award", "custom.award", Map.of());

        GamificationProcessingResult result = fixture.process("event:compensate", "core.quickfix.undone",
                Map.of("compensatesEventId", "event:award"));

        // "skip": al reconocerse como reversion, las reglas normales no se evaluan para este
        // evento, pese a que "conflicting" coincidiria con su tipo.
        assertThat(result.compensated()).hasSize(1);
        assertThat(result.granted()).isEmpty();
        assertThat(result.rejected()).isEmpty();
    }

    private static Fixture fixture(List<GamificationRule> rules) {
        GamificationDefinitionModel definitions = new GamificationDefinitionModel("1.0", "gamification-definitions",
                "metamodel", "Gamification", List.of(new PointTypeDefinition("points:quality", "Quality", "", 0)),
                List.of(), List.of(), List.of(), List.of(), Map.of());
        GamificationBindingModel bindings = new GamificationBindingModel("1.0", "gamification-bindings", "metamodel",
                definitions.id(), rules, Map.of());
        GamificationLoadResult configuration = new GamificationLoadResult(definitions, "definition-etag", bindings,
                "binding-etag", new GamificationCheckReport(definitions.id(), bindings.id(), List.of()));
        GamificationApplicationService application = new GamificationApplicationService(null, null, null, null, null, GamificationCorruptDocumentRecovery.disabled()) {
            @Override public GamificationLoadResult find(String metamodelId) { return configuration; }
        };
        InMemoryRuntimeRepository repository = new InMemoryRuntimeRepository();
        GamificationProperties properties = new GamificationProperties();
        InMemoryCanonicalEventBus bus = new InMemoryCanonicalEventBus();
        Fixture fixture = new Fixture(repository, new GamificationRuntimeService(application, repository, properties, bus));
        bus.subscribe(event -> event.eventTypeId().startsWith("core.gamification."), event -> fixture.published.add(event.eventTypeId()));
        return fixture;
    }

    private static GamificationRule award(long points) {
        return new GamificationRule("rule:award", "rule:award", "custom.award", "", "EQUALS", null, "points:quality",
                points, "", "", "", GamificationRepeatPolicy.ONCE_PER_EVENT, 0, 100, 100, GamificationEffectKind.AWARD, true);
    }

    private static CanonicalEvent event(String id, String type, Map<String, Object> payload) {
        return new CanonicalEvent("1.0", id, type, "1.0", Instant.parse("2026-07-28T10:00:00Z"),
                new EventSource("test", "gamification", "", "1.0"),
                new EventContext(EventContextKind.GAMIFICATION, "", "", "metamodel", "model", "", "session:one"),
                new EventActor("actor:one", EventActorKind.USER, "Actor"), null, "transaction:one", "correlation:one", "",
                payload, Map.of());
    }

    private static final class Fixture {
        private final InMemoryRuntimeRepository repository;
        private final GamificationRuntimeService runtime;
        private final java.util.ArrayList<String> published = new java.util.ArrayList<>();
        private Fixture(InMemoryRuntimeRepository repository, GamificationRuntimeService runtime) {
            this.repository = repository;
            this.runtime = runtime;
        }
        private GamificationProcessingResult process(String id, String type, Map<String, Object> payload) {
            return runtime.process("metamodel", event(id, type, payload));
        }
    }

    private static final class InMemoryRuntimeRepository implements GamificationRuntimeRepository {
        private GamificationRuntimeState state;
        private GamificationLedger ledger;
        private int revision;
        public Optional<StoredCanonicalDocument<GamificationRuntimeState>> findState(String id) { return Optional.ofNullable(state).map(this::stored); }
        public Optional<StoredCanonicalDocument<GamificationLedger>> findLedger(String id) { return Optional.ofNullable(ledger).map(this::stored); }
        public StoredCanonicalDocument<GamificationRuntimeState> saveState(GamificationRuntimeState value, String etag) { state = value; revision++; return stored(value); }
        public StoredCanonicalDocument<GamificationLedger> saveLedger(GamificationLedger value, String etag) { ledger = value; revision++; return stored(value); }
        public void deleteState(String id, String etag) { state = null; }
        public void deleteLedger(String id, String etag) { ledger = null; }
        public void reset(String id) { state = null; ledger = null; }
        private <T> StoredCanonicalDocument<T> stored(T value) { return new StoredCanonicalDocument<>(value, "etag-" + revision, Path.of("memory")); }
    }
}
