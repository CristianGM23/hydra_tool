package com.example.editor.event.bus;

import java.util.ArrayList;
import java.util.List;
import com.example.editor.event.CanonicalEvent;

/** Acumula eventos y solo los publica después de confirmar una operación. */
public final class EventPublicationBuffer {
    private final CanonicalEventBus bus;
    private final List<CanonicalEvent> pending = new ArrayList<>();
    private boolean closed;

    /** Descripción: Inicializa una instancia de EventPublicationBuffer con sus dependencias y datos de entrada. Entradas: bus: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public EventPublicationBuffer(CanonicalEventBus bus) {
        if(bus == null) throw new IllegalArgumentException("bus is required");
        this.bus = bus;
    }

    /** Descripción: Ejecuta la operación «add» correspondiente a la responsabilidad de EventPublicationBuffer. Entradas: event: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void add(CanonicalEvent event) {
        requireOpen();
        if(event == null) throw new IllegalArgumentException("event is required");
        pending.add(event);
    }

    /** Descripción: Ejecuta la operación «commit» correspondiente a la responsabilidad de EventPublicationBuffer. Entradas: no requiere parámetros de entrada. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void commit() {
        requireOpen();
        List<CanonicalEvent> committed = List.copyOf(pending);
        pending.clear();
        closed = true;
        List<RuntimeException> failures = new ArrayList<>();
        for(CanonicalEvent event : committed) {
            try { bus.publish(event); }
            catch(RuntimeException exception) { failures.add(exception); }
        }
        if(!failures.isEmpty()) throw new EventPublicationException(failures);
    }

    /** Descripción: Ejecuta la operación «rollback» correspondiente a la responsabilidad de EventPublicationBuffer. Entradas: no requiere parámetros de entrada. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void rollback() {
        requireOpen();
        pending.clear();
        closed = true;
    }

    /** Descripción: Ejecuta la operación «pendingCount» correspondiente a la responsabilidad de EventPublicationBuffer. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public int pendingCount() { return pending.size(); }

    /** Descripción: Valida las condiciones necesarias para «requireOpen» y rechaza entradas no válidas. Entradas: no requiere parámetros de entrada. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void requireOpen() {
        if(closed) throw new IllegalStateException("Publication buffer is closed");
    }
}
