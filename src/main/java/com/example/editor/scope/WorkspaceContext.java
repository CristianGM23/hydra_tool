package com.example.editor.scope;

/** Frontera neutral para resolver el workspace efectivo sin acoplar dominio a autenticación o transporte. */
public interface WorkspaceContext {
    /** Descripción: Ejecuta la operación «currentScope» correspondiente a la responsabilidad de WorkspaceContext. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ ResourceScope currentScope();
}
