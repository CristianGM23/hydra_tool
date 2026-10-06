package com.example.editor.audit;

import java.util.Map;

/** Actor por defecto en el modo local/single-user actual de HYDRA. */
public class LocalAuditActorContext implements AuditActorContext {
    @Override
    /** Descripción: Ejecuta la operación «currentActor» correspondiente a la responsabilidad de LocalAuditActorContext. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public AuditActor currentActor() {
        return new AuditActor("local-user", "Local user", "", Map.of("mode", "local"));
    }
}
