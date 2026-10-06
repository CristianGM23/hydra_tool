package com.example.editor.event.bus;
import static org.assertj.core.api.Assertions.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.example.editor.event.*;
class CanonicalEventBusTests {
    @Test void deliversOnlyMatchingEvents() { var bus = new InMemoryCanonicalEventBus(); var received = new ArrayList<CanonicalEvent>(); bus.subscribe("core.model.saved", received::add); bus.publish(event("core.model.saved", "event:1")); bus.publish(event("core.model.exported", "event:2")); assertThat(received).hasSize(1); }
    @Test void unsubscribeStopsDelivery() { var bus = new InMemoryCanonicalEventBus(); var received = new ArrayList<CanonicalEvent>(); var subscription = bus.subscribe(value -> true, received::add); subscription.close(); bus.publish(event("core.model.saved", "event:1")); assertThat(received).isEmpty(); assertThat(bus.subscriptionCount()).isZero(); }
    @Test void publicationBufferPublishesAfterCommit() { var bus = new InMemoryCanonicalEventBus(); var received = new ArrayList<CanonicalEvent>(); bus.subscribe(value -> true, received::add); var buffer = new EventPublicationBuffer(bus); buffer.add(event("core.model.saved", "event:1")); assertThat(received).isEmpty(); buffer.commit(); assertThat(received).hasSize(1); }
    @Test void publicationBufferDiscardsOnRollback() { var bus = new InMemoryCanonicalEventBus(); var received = new ArrayList<CanonicalEvent>(); bus.subscribe(value -> true, received::add); var buffer = new EventPublicationBuffer(bus); buffer.add(event("core.model.saved", "event:1")); buffer.rollback(); assertThat(received).isEmpty(); assertThatThrownBy(() -> buffer.add(event("core.model.saved", "event:2"))).isInstanceOf(IllegalStateException.class); }

    @Test void failingSubscriberDoesNotPreventOtherSubscribers() {
        var bus = new InMemoryCanonicalEventBus();
        var received = new ArrayList<CanonicalEvent>();
        bus.subscribe(value -> true, value -> { throw new IllegalStateException("broken"); });
        bus.subscribe(value -> true, received::add);
        assertThatThrownBy(() -> bus.publish(event("core.model.saved", "event:1"))).isInstanceOf(EventDeliveryException.class);
        assertThat(received).hasSize(1);
    }
    @Test void publicationBufferAttemptsEveryCommittedEvent() {
        var delivered = new ArrayList<CanonicalEvent>();
        CanonicalEventBus bus = new CanonicalEventBus() {
            public void publish(CanonicalEvent event) { if(event.id().equals("event:1")) throw new IllegalStateException("first"); delivered.add(event); }
            public EventSubscription subscribe(java.util.function.Predicate<CanonicalEvent> filter, CanonicalEventListener listener) { return () -> { }; }
        };
        var buffer = new EventPublicationBuffer(bus);
        buffer.add(event("core.model.saved", "event:1"));
        buffer.add(event("core.model.saved", "event:2"));
        assertThatThrownBy(buffer::commit).isInstanceOf(EventPublicationException.class);
        assertThat(delivered).extracting(CanonicalEvent::id).containsExactly("event:2");
        assertThat(buffer.pendingCount()).isZero();
    }

    @Test void nestedPublicationsAreDeliveredFifoAfterCurrentEventFinishes() {
        var bus = new InMemoryCanonicalEventBus();
        var order = new ArrayList<String>();
        bus.subscribe(value -> true, value -> {
            order.add("first:" + value.id());
            if(value.id().equals("event:1")) bus.publish(event("derived.saved", "event:2"));
        });
        bus.subscribe(value -> true, value -> order.add("second:" + value.id()));
        bus.publish(event("core.model.saved", "event:1"));
        assertThat(order).containsExactly("first:event:1", "second:event:1", "first:event:2", "second:event:2");
    }
    private CanonicalEvent event(String type, String id) { return new CanonicalEvent("1.0", id, type, "1.0", Instant.now(), new EventSource("TEST", "", "", ""), new EventContext(EventContextKind.SYSTEM, "", "", "", "", "", ""), null, null, "", "", "", Map.of(), Map.of()); }
}
