package com.example.editor.grammar.visualization;

/** Normalized warning emitted while inferring the metamodel/class diagram. */
public record GrammarProjectionWarning(String code, String path, String message) {
    public GrammarProjectionWarning { code=text(code);path=text(path);message=text(message); }
    /** Descripción: Ejecuta la operación «text» correspondiente a la responsabilidad de GrammarProjectionWarning. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String text(String value){return value==null?"":value.trim();}
}
