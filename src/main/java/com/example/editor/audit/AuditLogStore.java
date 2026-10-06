package com.example.editor.audit;

import java.time.Instant;
import java.util.List;

/** SPI de persistencia. Local JSONL es el proveedor actual y puede sustituirse sin tocar el dominio. */
public interface AuditLogStore {
    /** Descripción: Ejecuta la operación «appendBatch» correspondiente a la responsabilidad de AuditEntrySink. Entradas: entries: colección de elementos que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ void appendBatch(List<AuditEntry> entries);
    /** Descripción: Ejecuta la operación «query» correspondiente a la responsabilidad de AuditEntrySink. Entradas: query: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ AuditPage query(AuditQuery query);
    /** Descripción: Ejecuta la operación «statistics» correspondiente a la responsabilidad de AuditEntrySink. Entradas: query: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ AuditStatistics statistics(AuditQuery query);
    /** Descripción: Ejecuta la operación «stream» correspondiente a la responsabilidad de AuditEntrySink. Entradas: query: dato de entrada necesario para la operación; sink: dato de entrada necesario para la operación; maximumEntries: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ long stream(AuditQuery query, AuditEntrySink sink, long maximumEntries);
    /** Descripción: Ejecuta la operación «purgeBefore» correspondiente a la responsabilidad de AuditEntrySink. Entradas: cutoff: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ void purgeBefore(Instant cutoff);

    @FunctionalInterface
    interface AuditEntrySink {
        /** Descripción: Ejecuta la operación «accept» correspondiente a la responsabilidad de AuditEntrySink. Entradas: entry: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ void accept(AuditEntry entry);
    }
}
