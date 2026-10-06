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

/** Cross-layer stress test: verified events must produce bounded, idempotent gamification effects. */
class AggressiveEventGamificationSynergyTests {

    @Test
    void oneHundredVerifiedEventsProduceExactlyOneHundredLedgerEntriesAndPoints() {
        Fixture fixture = fixture();
        for (int index = 0; index < 100; index++) fixture.process("event:" + index);
        assertThat(fixture.repository.state.actors().get("actor:one").points()).containsEntry("points:quality", 100L);
        assertThat(fixture.repository.ledger.entries()).hasSize(100);
        assertThat(fixture.runtime.feedback("metamodel", "actor:one", 200)).hasSize(100);
        assertThat(fixture.published).hasSize(100).allMatch(type -> type.equals("core.gamification.award-granted"));
    }

    @Test
    void replayingTheSameHundredEventsDoesNotDuplicateAnyCrossLayerSideEffect() {
        Fixture fixture = fixture();
        for (int index = 0; index < 100; index++) fixture.process("event:" + index);
        int revision = fixture.repository.revision;
        int published = fixture.published.size();
        for (int index = 99; index >= 0; index--) {
            GamificationProcessingResult duplicate = fixture.process("event:" + index);
            assertThat(duplicate.granted()).isEmpty();
            assertThat(duplicate.feedback()).isEmpty();
        }
        assertThat(fixture.repository.ledger.entries()).hasSize(100);
        assertThat(fixture.repository.state.actors().get("actor:one").points()).containsEntry("points:quality", 100L);
        assertThat(fixture.repository.revision).isEqualTo(revision);
        assertThat(fixture.published).hasSize(published);
    }

    private static Fixture fixture() {
        GamificationDefinitionModel definitions = new GamificationDefinitionModel("1.0", "gamification-definitions", "metamodel", "Gamification",
                List.of(new PointTypeDefinition("points:quality", "Quality", "", 0)), List.of(), List.of(), List.of(), List.of(), Map.of());
        GamificationRule rule = new GamificationRule("rule:verified", "Verified", "core.validation.violation-resolved", "", "EQUALS", null,
                "points:quality", 1, "", "", "", GamificationRepeatPolicy.ONCE_PER_EVENT, 0, 0, 0, GamificationEffectKind.AWARD, true);
        GamificationBindingModel bindings = new GamificationBindingModel("1.0", "gamification-bindings", "metamodel", definitions.id(), List.of(rule), Map.of());
        GamificationLoadResult configuration = new GamificationLoadResult(definitions, "definition-etag", bindings, "binding-etag",
                new GamificationCheckReport(definitions.id(), bindings.id(), List.of()));
        GamificationApplicationService application = new GamificationApplicationService(null, null, null, null, null, GamificationCorruptDocumentRecovery.disabled()) {
            @Override public GamificationLoadResult find(String metamodelId) { return configuration; }
        };
        InMemoryRuntimeRepository repository = new InMemoryRuntimeRepository();
        InMemoryCanonicalEventBus bus = new InMemoryCanonicalEventBus();
        java.util.ArrayList<String> published = new java.util.ArrayList<>();
        bus.subscribe(event -> event.eventTypeId().startsWith("core.gamification."), event -> published.add(event.eventTypeId()));
        GamificationRuntimeService runtime = new GamificationRuntimeService(application, repository, new GamificationProperties(), bus);
        return new Fixture(repository, runtime, published);
    }

    private static CanonicalEvent event(String id) {
        return new CanonicalEvent("1.0", id, "core.validation.violation-resolved", "1.0", Instant.parse("2026-09-29T08:00:00Z"),
                new EventSource("test", "validation", "", "1.0"),
                new EventContext(EventContextKind.VALIDATION, "", "", "metamodel", "model", "", "session:aggressive"),
                new EventActor("actor:one", EventActorKind.USER, "Actor"), null, "transaction:" + id, "correlation:" + id, "", Map.of(), Map.of());
    }

    private record Fixture(InMemoryRuntimeRepository repository, GamificationRuntimeService runtime, java.util.ArrayList<String> published) {
        GamificationProcessingResult process(String id) { return runtime.process("metamodel", event(id)); }
    }

    private static final class InMemoryRuntimeRepository implements GamificationRuntimeRepository {
        private GamificationRuntimeState state;
        private GamificationLedger ledger;
        private int revision;
        @Override public Optional<StoredCanonicalDocument<GamificationRuntimeState>> findState(String id) { return Optional.ofNullable(state).map(this::stored); }
        @Override public Optional<StoredCanonicalDocument<GamificationLedger>> findLedger(String id) { return Optional.ofNullable(ledger).map(this::stored); }
        @Override public StoredCanonicalDocument<GamificationRuntimeState> saveState(GamificationRuntimeState value, String etag) { state=value; revision++; return stored(value); }
        @Override public StoredCanonicalDocument<GamificationLedger> saveLedger(GamificationLedger value, String etag) { ledger=value; revision++; return stored(value); }
        @Override public void deleteState(String id, String etag) { state=null; }
        @Override public void deleteLedger(String id, String etag) { ledger=null; }
        @Override public void reset(String id) { state=null; ledger=null; }
        private <T> StoredCanonicalDocument<T> stored(T value) { return new StoredCanonicalDocument<>(value,"etag-"+revision,Path.of("memory")); }
    }
}
