package com.example.editor.integration.bridge;
/** Transacción que revierte automáticamente si se cierra sin commit. */
public interface HostTransactionScope extends AutoCloseable {
    /** Descripción: Ejecuta la operación «transactionId» correspondiente a la responsabilidad de HostTransactionScope. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ String transactionId();
    /** Descripción: Ejecuta la operación «commit» correspondiente a la responsabilidad de HostTransactionScope. Entradas: no requiere parámetros de entrada. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ void commit();
    /** Descripción: Ejecuta la operación «rollback» correspondiente a la responsabilidad de HostTransactionScope. Entradas: no requiere parámetros de entrada. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ void rollback();
    /** Descripción: Elimina o libera el recurso gestionado por «closed» de forma controlada. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ boolean closed();
    /** Descripción: Elimina o libera el recurso gestionado por «close» de forma controlada. Entradas: no requiere parámetros de entrada. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ @Override default void close() { if(!closed()) rollback(); }
}
