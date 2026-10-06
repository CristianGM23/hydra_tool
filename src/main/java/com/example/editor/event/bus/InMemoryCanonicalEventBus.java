package com.example.editor.event.bus;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import org.springframework.stereotype.Component;
import com.example.editor.event.CanonicalEvent;

/**
 * Bus síncrono, neutral y en memoria. Las publicaciones reentrantes se encolan
 * en FIFO para que un evento derivado nunca interrumpa la entrega del evento
 * que lo causó. Esto hace deterministas bindings, EventFlow y gamificación.
 */
@Component
public class InMemoryCanonicalEventBus implements CanonicalEventBus {
    private static final int MAX_EVENTS_PER_PUBLICATION = 4096;
    private record Registered(Predicate<CanonicalEvent> filter, CanonicalEventListener listener) { }
    private static final class PublicationState {
        final Deque<CanonicalEvent> pending = new ArrayDeque<>();
        final List<RuntimeException> failures = new ArrayList<>();
        boolean draining;
        int delivered;
    }

    private final CopyOnWriteArrayList<Registered> listeners = new CopyOnWriteArrayList<>();
    private final ThreadLocal<PublicationState> publicationState = ThreadLocal.withInitial(PublicationState::new);

    /** Publica de forma síncrona respetando orden FIFO también para eventos derivados. */
    @Override
    /** Descripción: Procesa la operación «publish» coordinando las responsabilidades de PublicationState. Entradas: event: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void publish(CanonicalEvent event) {
        if(event == null) throw new IllegalArgumentException("event must not be null");
        PublicationState state = publicationState.get();
        state.pending.addLast(event);
        if(state.draining) return;
        state.draining = true;
        String rootEventId = event.id();
        try {
            while(!state.pending.isEmpty()) {
                if(++state.delivered > MAX_EVENTS_PER_PUBLICATION) {
                    state.pending.clear();
                    state.failures.add(new IllegalStateException(
                            "Canonical event publication exceeded " + MAX_EVENTS_PER_PUBLICATION
                                    + " events; a binding/flow cycle is likely"));
                    break;
                }
                CanonicalEvent current = state.pending.removeFirst();
                for(Registered registered : listeners) {
                    try {
                        if(registered.filter().test(current)) registered.listener().onEvent(current);
                    } catch(RuntimeException exception) {
                        state.failures.add(exception);
                    }
                }
            }
            if(!state.failures.isEmpty()) throw new EventDeliveryException(rootEventId, state.failures);
        } finally {
            state.pending.clear();
            state.failures.clear();
            state.draining = false;
            state.delivered = 0;
            publicationState.remove();
        }
    }

    @Override
    /** Descripción: Ejecuta la operación «subscribe» correspondiente a la responsabilidad de PublicationState. Entradas: filter: dato de entrada necesario para la operación; listener: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public EventSubscription subscribe(Predicate<CanonicalEvent> filter, CanonicalEventListener listener) {
        if(filter == null || listener == null) throw new IllegalArgumentException("filter and listener are required");
        Registered registered = new Registered(filter, listener);
        listeners.add(registered);
        return () -> listeners.remove(registered);
    }

    /** Descripción: Ejecuta la operación «subscriptionCount» correspondiente a la responsabilidad de PublicationState. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public int subscriptionCount() { return listeners.size(); }
}
