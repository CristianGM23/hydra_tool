package com.example.editor.event;
import static org.assertj.core.api.Assertions.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
class EventContractTests {
    @Test void createsCanonicalEventWithImmutablePayload() {
        var event = event();
        assertThat(event.schemaVersion()).isEqualTo("1.0");
        assertThat(event.payload()).containsEntry("ruleId", "rule:age");
        assertThatThrownBy(() -> event.payload().put("x", 1)).isInstanceOf(UnsupportedOperationException.class);
    }
    @Test void rejectsInvalidEventIdentifiers() { assertThatThrownBy(() -> new CanonicalEvent("1.0", "bad id", "core.model.saved", "1.0", Instant.now(), source(), context(), null, null, "", "", "", Map.of(), Map.of())).isInstanceOf(IllegalArgumentException.class); }
    @Test void keepsCorrelationAndCausation() { var event = new CanonicalEvent("1.0", "event:2", "core.model.saved", "1.0", Instant.now(), source(), context(), null, null, "tx:1", "corr:1", "event:1", Map.of(), Map.of()); assertThat(event.transactionId()).isEqualTo("tx:1"); assertThat(event.causationId()).isEqualTo("event:1"); }
    @Test void requiresExactlyOnePayloadMappingSource() { assertThatThrownBy(() -> new EventPayloadMapping("field:value", "payload.value", "expression:value", null)).isInstanceOf(IllegalArgumentException.class); }
    @Test void validatesTypedEventFields() { assertThatThrownBy(() -> new EventFieldDefinition("field:value", "value", EventFieldType.ENUM, true, false, "", "", "")).isInstanceOf(IllegalArgumentException.class); }
    @Test void exposesReservedTypesWithoutAllowingModification() { assertThat(ReservedEventCatalog.eventTypeIds()).contains("core.validation.violation-resolved", "core.quickfix.applied"); assertThatThrownBy(() -> ReservedEventCatalog.eventTypeIds().add("x")).isInstanceOf(UnsupportedOperationException.class); }
    private CanonicalEvent event() { return new CanonicalEvent("1.0", "event:1", "core.validation.violation-resolved", "1.0", Instant.parse("2026-07-26T18:00:00Z"), source(), context(), new EventActor("user:1", EventActorKind.USER, "User"), new EventSubject(EventSubjectKind.MODEL_ELEMENT, "people-model", "person:1", "class:Person", "attribute:Person:age", "elements/person:1"), "tx:1", "corr:1", "", Map.of("ruleId", "rule:age"), Map.of()); }
    private EventSource source() { return new EventSource("VALIDATION_ENGINE", "validation", "", "1.0"); }
    private EventContext context() { return new EventContext(EventContextKind.VALIDATION, "", "", "people", "people-model", "people-editor", "session:1"); }
}
