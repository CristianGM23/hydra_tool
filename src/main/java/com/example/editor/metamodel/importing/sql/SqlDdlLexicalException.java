package com.example.editor.metamodel.importing.sql;

/** Precise lexer failure used to report malformed quoted/commented SQL before persistence. */
public final class SqlDdlLexicalException extends RuntimeException {
    private final int line;
    private final int column;
    /** Descripción: Inicializa una instancia de SqlDdlLexicalException con ubicación precisa. Entradas: line, column y message. Salida: excepción lista para diagnóstico. */ public SqlDdlLexicalException(int line,int column,String message){super(message);this.line=line;this.column=column;}
    /** Descripción: Devuelve la línea del error léxico. Entradas: no requiere parámetros. Salida: número de línea. */ public int line(){return line;}
    /** Descripción: Devuelve la columna del error léxico. Entradas: no requiere parámetros. Salida: número de columna. */ public int column(){return column;}
}
