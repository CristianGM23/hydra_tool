package com.example.editor.metamodel.importing.sql;

/** Raised before mutation when an SQL DDL resource exceeds a configured safety budget. */
public final class SqlDdlSafetyException extends RuntimeException {
    /** Descripción: Inicializa una instancia de SqlDdlSafetyException con sus dependencias y datos de entrada. Entradas: message: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public SqlDdlSafetyException(String message) { super(message); }
}
