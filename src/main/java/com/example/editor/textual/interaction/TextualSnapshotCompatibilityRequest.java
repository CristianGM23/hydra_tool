package com.example.editor.textual.interaction;

/** Evidencia mínima para decidir si una sesión puede reutilizar un snapshot gramatical. */
public record TextualSnapshotCompatibilityRequest(String previousSnapshotId,String currentSnapshotId,String previousGrammarId,String currentGrammarId,String previousSemanticContractHash,String currentSemanticContractHash,String previousProvider,String currentProvider){
    public TextualSnapshotCompatibilityRequest{previousSnapshotId=text(previousSnapshotId);currentSnapshotId=text(currentSnapshotId);previousGrammarId=text(previousGrammarId);currentGrammarId=text(currentGrammarId);previousSemanticContractHash=text(previousSemanticContractHash);currentSemanticContractHash=text(currentSemanticContractHash);previousProvider=text(previousProvider);currentProvider=text(currentProvider);}
    /** Descripción: Normaliza texto opcional de evidencia de snapshot. Entradas: value: valor de entrada. Salida: devuelve texto no nulo y recortado. */ private static String text(String value){return value==null?"":value.trim();}
}
