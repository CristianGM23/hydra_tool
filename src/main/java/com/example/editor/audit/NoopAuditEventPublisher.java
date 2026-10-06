package com.example.editor.audit;

/** Publisher de compatibilidad para construcciones manuales/tests que no levantan Spring. */
public enum NoopAuditEventPublisher implements AuditEventPublisher {
    INSTANCE;
    /** Descripción: Procesa la operación «publish» coordinando las responsabilidades de NoopAuditEventPublisher. Entradas: event: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ @Override public void publish(AuditEvent event) { }
}
