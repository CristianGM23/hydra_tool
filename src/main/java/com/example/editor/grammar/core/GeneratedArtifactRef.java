package com.example.editor.grammar.core;
public record GeneratedArtifactRef(String path, String sha256, long sizeBytes, String kind) {
    public GeneratedArtifactRef { path=text(path); sha256=text(sha256); sizeBytes=Math.max(0,sizeBytes); kind=text(kind); }
    /** Descripción: Ejecuta la operación «text» correspondiente a la responsabilidad de GeneratedArtifactRef. Entradas: v: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String text(String v){return v==null?"":v.trim();}
}
