package com.example.editor.grammar.core;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Explicit FULL compilation request; compile is never triggered by keystrokes. */
public record GrammarCompilationRequest(String requestId, String grammarId, long sourceRevision,
        List<GrammarSourceDocument> documents, String toolchainProfileId, String expectedSourceHash,
        boolean activateOnSuccess, Map<String,String> options) {
    public GrammarCompilationRequest {
        requestId=text(requestId);if(requestId.isBlank())requestId="grammar-compile:"+UUID.randomUUID();
        grammarId=text(grammarId);sourceRevision=Math.max(0,sourceRevision);documents=documents==null?List.of():List.copyOf(documents);
        toolchainProfileId=text(toolchainProfileId);expectedSourceHash=text(expectedSourceHash);options=options==null?Map.of():Map.copyOf(options);
        if(!expectedSourceHash.isBlank()&&!documents.isEmpty()&&!expectedSourceHash.equalsIgnoreCase(GrammarHashing.sources(documents)))
            throw new IllegalArgumentException("Compilation expectedSourceHash does not match supplied documents");
    }
    /** Descripción: Inicializa una instancia de GrammarCompilationRequest con sus dependencias y datos de entrada. Entradas: grammarId: identificador que delimita el recurso objetivo; sourceRevision: dato de entrada necesario para la operación; documents: dato de entrada necesario para la operación; toolchainProfileId: identificador que delimita el recurso objetivo; activateOnSuccess: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public GrammarCompilationRequest(String grammarId,long sourceRevision,List<GrammarSourceDocument>documents,String toolchainProfileId,boolean activateOnSuccess){
        this("",grammarId,sourceRevision,documents,toolchainProfileId,"",activateOnSuccess,Map.of());
    }
    /** Descripción: Ejecuta la operación «text» correspondiente a la responsabilidad de GrammarCompilationRequest. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String text(String value){return value==null?"":value.trim();}
}
