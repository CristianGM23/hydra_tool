package com.example.editor.event.bus;

import java.util.List;

/** Agrupa fallos de publicación ocurridos después de confirmar una operación. */
public class EventPublicationException extends RuntimeException {
    private final List<RuntimeException> failures;

    /** Descripción: Inicializa una instancia de EventPublicationException con sus dependencias y datos de entrada. Entradas: failures: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public EventPublicationException(List<RuntimeException> failures) {
        super("One or more committed events could not be delivered: " + failures.size());
        this.failures = List.copyOf(failures);
        failures.forEach(this::addSuppressed);
    }

    /** Descripción: Ejecuta la operación «failures» correspondiente a la responsabilidad de EventPublicationException. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public List<RuntimeException> failures() { return failures; }
}
