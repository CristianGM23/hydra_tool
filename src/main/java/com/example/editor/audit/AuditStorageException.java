package com.example.editor.audit;

public class AuditStorageException extends RuntimeException {
    /** Descripción: Inicializa una instancia de AuditStorageException con sus dependencias y datos de entrada. Entradas: message: dato de entrada necesario para la operación; cause: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public AuditStorageException(String message, Throwable cause) { super(message, cause); }
}
