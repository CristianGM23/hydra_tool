package com.example.editor.audit;

import java.util.List;

/** SPI para diferencias de dominio. La estrategia genérica cubre cualquier módulo nuevo por defecto. */
public interface AuditDiffStrategy {
    /** Descripción: Ejecuta la operación «order» correspondiente a la responsabilidad de AuditDiffResult. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ default int order() { return 1000; }
    /** Descripción: Evalúa la condición «supports» sin modificar el estado externo. Entradas: event: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ boolean supports(AuditCapturedEvent event);
    /** Descripción: Ejecuta la operación «diff» correspondiente a la responsabilidad de AuditDiffResult. Entradas: event: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ AuditDiffResult diff(AuditCapturedEvent event);

    record AuditDiffResult(List<AuditChange> changes, boolean truncated) {
        public AuditDiffResult { changes = changes == null ? List.of() : List.copyOf(changes); }
    }
}
