package com.example.editor.grammar.runtime;

import java.time.Instant;
public record GrammarActivationState(String grammarId, String snapshotId, String sourceHash, Instant activatedAt) {
    public GrammarActivationState { grammarId=text(grammarId); snapshotId=text(snapshotId); sourceHash=text(sourceHash); activatedAt=activatedAt==null?Instant.now():activatedAt; }
    /** Descripción: Ejecuta la operación «text» correspondiente a la responsabilidad de GrammarActivationState. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String text(String value){return value==null?"":value.trim();}
}
