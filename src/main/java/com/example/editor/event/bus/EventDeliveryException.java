package com.example.editor.event.bus;

import java.util.List;

/** Informa de fallos de consumidores sin impedir que los demás reciban el evento. */
public class EventDeliveryException extends RuntimeException {
    private final String eventId;
    private final List<RuntimeException> failures;

    /** Descripción: Inicializa una instancia de EventDeliveryException con sus dependencias y datos de entrada. Entradas: eventId: identificador que delimita el recurso objetivo; failures: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public EventDeliveryException(String eventId, List<RuntimeException> failures) {
        super("Event delivery failed for " + eventId + " in " + failures.size() + " subscriber(s)");
        this.eventId = eventId;
        this.failures = List.copyOf(failures);
        failures.forEach(this::addSuppressed);
    }

    /** Descripción: Ejecuta la operación «eventId» correspondiente a la responsabilidad de EventDeliveryException. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public String eventId() { return eventId; }
    /** Descripción: Ejecuta la operación «failures» correspondiente a la responsabilidad de EventDeliveryException. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public List<RuntimeException> failures() { return failures; }
}
