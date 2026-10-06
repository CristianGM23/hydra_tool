package com.example.editor.grammar.core;
public record GrammarSymbol(String id, String name, String kind, String documentId, TextRange range, String parentId, String detail) {
    public GrammarSymbol { id=text(id); name=text(name); kind=text(kind); documentId=text(documentId); range=range==null?new TextRange(new TextPosition(0,0),new TextPosition(0,0)):range; parentId=text(parentId); detail=text(detail); }
    /** Descripción: Ejecuta la operación «text» correspondiente a la responsabilidad de GrammarSymbol. Entradas: v: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String text(String v){return v==null?"":v.trim();}
}
