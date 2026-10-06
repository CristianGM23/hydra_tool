package com.example.editor.integration.bridge;
/** Abre transacciones sin imponer EMF, Eclipse, VS Code o BESSER al núcleo. */
public interface HostTransactionBridge extends HostIntegrationBridge {
    /** Descripción: Ejecuta la operación «begin» correspondiente a la responsabilidad de HostTransactionBridge. Entradas: correlationId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ HostTransactionScope begin(String correlationId);
    /** Descripción: Evalúa la condición «supportsUndo» sin modificar el estado externo. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ boolean supportsUndo();
    /** Descripción: Ejecuta la operación «undo» correspondiente a la responsabilidad de HostTransactionBridge. Entradas: no requiere parámetros de entrada. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ void undo();
    /** Descripción: Ejecuta la operación «redo» correspondiente a la responsabilidad de HostTransactionBridge. Entradas: no requiere parámetros de entrada. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ void redo();
}
