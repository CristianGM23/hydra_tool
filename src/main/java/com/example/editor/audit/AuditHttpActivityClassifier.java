package com.example.editor.audit;

import java.util.Optional;

/** SPI abierto: un módulo futuro puede clasificar sus endpoints sin modificar el filtro central. */
public interface AuditHttpActivityClassifier {
    /** Descripción: Ejecuta la operación «classify» correspondiente a la responsabilidad de AuditHttpActivityClassifier. Entradas: method: dato de entrada necesario para la operación; requestPath: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ Optional<AuditHttpActivityClassification> classify(String method, String requestPath);
    /** Descripción: Ejecuta la operación «priority» correspondiente a la responsabilidad de AuditHttpActivityClassifier. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ default int priority() { return 0; }
}
