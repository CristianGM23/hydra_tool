package com.example.editor.metamodel.importing.sql;

public record SqlDdlToken(SqlDdlTokenType type, String text, int line, int column) {
    /** Descripción: Evalúa la condición «is» sin modificar el estado externo. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean is(String value) { return text.equalsIgnoreCase(value); }
}
