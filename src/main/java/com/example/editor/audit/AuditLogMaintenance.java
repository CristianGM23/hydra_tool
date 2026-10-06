package com.example.editor.audit;

/** SPI opcional para providers que soporten mantenimiento y borrado físico. */
public interface AuditLogMaintenance {
    /** Descripción: Ejecuta la operación «storageStatus» correspondiente a la responsabilidad de AuditLogMaintenance. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ AuditStorageStatus storageStatus();
    /** Descripción: Ejecuta la operación «maintain» correspondiente a la responsabilidad de AuditLogMaintenance. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ AuditMaintenanceResult maintain();
    /** Descripción: Elimina o libera el recurso gestionado por «delete» de forma controlada. Entradas: criteria: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ AuditMaintenanceResult delete(AuditDeletionCriteria criteria);
}
