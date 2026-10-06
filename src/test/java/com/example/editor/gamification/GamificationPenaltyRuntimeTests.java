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

class GamificationPenaltyRuntimeTests {
    @Test void subtractsPointsFromAnExistingBalance() {
        Fixture fixture = fixture(-20, List.of(award(10), penalty(3)));
        fixture.process("event:award", "custom.award");
        GamificationProcessingResult result = fixture.process("event:penalty", "custom.penalty");
        assertThat(result.state().actors().get("actor:one").points()).containsEntry("points:quality", 7L);
        assertThat(result.granted()).singleElement().extracting(GamificationLedgerEntry::pointsDelta).isEqualTo(-3L);
    }

    @Test void clampsPenaltyAtConfiguredMinimumBalance() {
        Fixture fixture = fixture(-5, List.of(penalty(12)));
        GamificationProcessingResult result = fixture.process("event:penalty", "custom.penalty");
        assertThat(result.state().actors().get("actor:one").points()).containsEntry("points:quality", -5L);
        assertThat(result.granted()).singleElement().extracting(GamificationLedgerEntry::pointsDelta).isEqualTo(-5L);
    }

    @Test void rejectsPenaltyWhenActorAlreadyReachedTheFloor() {
        Fixture fixture = fixture(-5, List.of(penalty(5)));
        fixture.process("event:first", "custom.penalty");
        GamificationProcessingResult result = fixture.process("event:second", "custom.penalty");
        assertThat(result.granted()).isEmpty();
        assertThat(result.rejected()).singleElement().satisfies(entry -> {
            assertThat(entry.pointsDelta()).isZero();
            assertThat(entry.metadata()).containsEntry("reason", "Minimum point balance reached");
        });
    }

    @Test void publishesDedicatedPenaltyEventAfterCommit() {
        Fixture fixture = fixture(-20, List.of(penalty(2)));
        fixture.process("event:penalty", "custom.penalty");
        assertThat(fixture.published).contains("core.gamification.penalty-applied");
    }

    @Test void persistsNegativeLedgerEntry() {
        Fixture fixture = fixture(-20, List.of(penalty(4)));
        fixture.process("event:penalty", "custom.penalty");
        assertThat(fixture.repository.ledger.entries()).singleElement().satisfies(entry -> {
            assertThat(entry.pointsDelta()).isEqualTo(-4L);
            assertThat(entry.metadata()).containsEntry("effectKind", "PENALTY");
        });
    }


    @Test void emitsPortablePenaltyFeedbackAfterCommit() {
        Fixture fixture = fixture(-20, List.of(penalty(4)));
        GamificationProcessingResult result = fixture.process("event:penalty", "custom.penalty");
        assertThat(result.feedback()).singleElement().satisfies(item -> {
            assertThat(item.kind().name()).isEqualTo("PENALTY_APPLIED");
            assertThat(item.severity().name()).isEqualTo("WARNING");
            assertThat(item.pointsDelta()).isEqualTo(-4L);
            assertThat(item.sourceEventId()).isEqualTo("event:penalty");
        });
        assertThat(fixture.runtime.feedback("metamodel", "actor:one", 10))
                .extracting(com.example.editor.gamification.feedback.UserFeedbackEnvelope::id)
                .containsExactlyElementsOf(result.feedback().stream().map(com.example.editor.gamification.feedback.UserFeedbackEnvelope::id).toList());
    }

    @Test void duplicateEventDoesNotEmitDuplicateFeedback() {
        Fixture fixture = fixture(-20, List.of(penalty(3)));
        fixture.process("event:penalty", "custom.penalty");
        GamificationProcessingResult duplicate = fixture.process("event:penalty", "custom.penalty");
        assertThat(duplicate.feedback()).isEmpty();
        assertThat(fixture.runtime.feedback("metamodel", "actor:one", 100)).hasSize(1);
    }

    @Test void processingSameEventAndRuleIsIdempotent() {
        Fixture fixture = fixture(-20, List.of(penalty(3)));
        fixture.process("event:penalty", "custom.penalty");
        GamificationProcessingResult duplicate = fixture.process("event:penalty", "custom.penalty");
        assertThat(duplicate.granted()).isEmpty();
        assertThat(fixture.repository.ledger.entries()).hasSize(1);
    }

    @Test void duplicateEventDoesNotRewriteRuntimeDocumentsOrRepublishSideEffects() {
        Fixture fixture = fixture(-20, List.of(penalty(3)));
        fixture.process("event:penalty", "custom.penalty");
        int revisionAfterFirstCommit = fixture.repository.revision;
        int publishedAfterFirstCommit = fixture.published.size();
        GamificationProcessingResult duplicate = fixture.process("event:penalty", "custom.penalty");
        assertThat(fixture.repository.revision).isEqualTo(revisionAfterFirstCommit);
        assertThat(fixture.published).hasSize(publishedAfterFirstCommit);
        assertThat(duplicate.granted()).isEmpty();
        assertThat(duplicate.rejected()).isEmpty();
        assertThat(duplicate.compensated()).isEmpty();
        assertThat(duplicate.feedback()).isEmpty();
    }


    private static Fixture fixture(long minimumBalance, List<GamificationRule> rules) {
        GamificationDefinitionModel definitions = new GamificationDefinitionModel("1.0", "gamification-definitions",
                "metamodel", "Gamification", List.of(new PointTypeDefinition("points:quality", "Quality", "", minimumBalance)),
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
        return rule("rule:award", "custom.award", points, GamificationEffectKind.AWARD);
    }
    private static GamificationRule penalty(long points) {
        return rule("rule:penalty", "custom.penalty", points, GamificationEffectKind.PENALTY);
    }
    private static GamificationRule rule(String id, String eventTypeId, long points, GamificationEffectKind effect) {
        return new GamificationRule(id, id, eventTypeId, "", "EQUALS", null, "points:quality", points, "", "", "",
                GamificationRepeatPolicy.ONCE_PER_EVENT, 0, 100, 100, effect, true);
    }

    private static CanonicalEvent event(String id, String type) {
        return new CanonicalEvent("1.0", id, type, "1.0", Instant.parse("2026-07-28T10:00:00Z"),
                new EventSource("test", "gamification", "", "1.0"),
                new EventContext(EventContextKind.GAMIFICATION, "", "", "metamodel", "model", "", "session:one"),
                new EventActor("actor:one", EventActorKind.USER, "Actor"), null, "transaction:one", "correlation:one", "",
                Map.of(), Map.of());
    }

    private static final class Fixture {
        private final InMemoryRuntimeRepository repository;
        private final GamificationRuntimeService runtime;
        private final java.util.ArrayList<String> published = new java.util.ArrayList<>();
        private Fixture(InMemoryRuntimeRepository repository, GamificationRuntimeService runtime) {
            this.repository = repository;
            this.runtime = runtime;
        }
        private GamificationProcessingResult process(String id, String type) { return runtime.process("metamodel", event(id, type)); }
    }

    private static final class InMemoryRuntimeRepository implements GamificationRuntimeRepository {
        private GamificationRuntimeState state;
        private GamificationLedger ledger;
        private int revision;
        public Optional<StoredCanonicalDocument<GamificationRuntimeState>> findState(String id) { return Optional.ofNullable(state).map(value -> stored(value)); }
        public Optional<StoredCanonicalDocument<GamificationLedger>> findLedger(String id) { return Optional.ofNullable(ledger).map(value -> stored(value)); }
        public StoredCanonicalDocument<GamificationRuntimeState> saveState(GamificationRuntimeState value, String etag) { state = value; revision++; return stored(value); }
        public StoredCanonicalDocument<GamificationLedger> saveLedger(GamificationLedger value, String etag) { ledger = value; revision++; return stored(value); }
        public void deleteState(String id, String etag) { state = null; }
        public void deleteLedger(String id, String etag) { ledger = null; }
        public void reset(String id) { state = null; ledger = null; }
        private <T> StoredCanonicalDocument<T> stored(T value) { return new StoredCanonicalDocument<>(value, "etag-" + revision, Path.of("memory")); }
    }
}
