package com.example.editor.event.bus;
import java.util.function.Predicate;
import com.example.editor.event.CanonicalEvent;
public interface CanonicalEventBus {
    /** Descripción: Procesa la operación «publish» coordinando las responsabilidades de CanonicalEventBus. Entradas: event: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ void publish(CanonicalEvent event);
    /** Descripción: Ejecuta la operación «subscribe» correspondiente a la responsabilidad de CanonicalEventBus. Entradas: filter: dato de entrada necesario para la operación; listener: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ EventSubscription subscribe(Predicate<CanonicalEvent> filter, CanonicalEventListener listener);
    /** Descripción: Ejecuta la operación «subscribe» correspondiente a la responsabilidad de CanonicalEventBus. Entradas: eventTypeId: identificador que delimita el recurso objetivo; listener: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ default EventSubscription subscribe(String eventTypeId, CanonicalEventListener listener) { return subscribe(event -> event.eventTypeId().equals(eventTypeId), listener); }
}
