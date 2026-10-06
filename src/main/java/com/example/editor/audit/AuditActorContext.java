package com.example.editor.audit;

/** Punto de extensión para resolver el usuario/actor efectivo sin acoplar auditoría a autenticación. */
public interface AuditActorContext {
    /** Descripción: Ejecuta la operación «currentActor» correspondiente a la responsabilidad de AuditActorContext. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ AuditActor currentActor();
}
