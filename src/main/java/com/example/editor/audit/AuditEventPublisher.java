package com.example.editor.audit;

/** API mínima que usan los módulos; el núcleo de auditoría se mantiene desacoplado de ellos. */
public interface AuditEventPublisher {
    /** Descripción: Procesa la operación «publish» coordinando las responsabilidades de AuditEventPublisher. Entradas: event: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ void publish(AuditEvent event);

    /** Fuerza, cuando el provider lo soporte, la persistencia de eventos pendientes antes de una consulta. */
    /** Descripción: Ejecuta la operación «flushPending» correspondiente a la responsabilidad de AuditEventPublisher. Entradas: no requiere parámetros de entrada. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ default void flushPending() { }
}
