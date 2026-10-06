package com.example.editor.textual.runtime;

import java.time.Instant;
import java.util.*;
import com.example.editor.textual.source.*;
import com.example.editor.validation.model.CanonicalModelDocument;

/** Textual editing session. Broken drafts never replace lastValidSemanticModel or lastCommittedSemanticModel. */
public record TextualModelSession(
        String id,String editorDefinitionId,String grammarId,String grammarSnapshotId,
        List<TextualModelSourceDocument> documents,long draftRevision,long lastParsedRevision,String lastParsedSourceHash,
        CanonicalModelDocument lastValidSemanticModel,List<TextualSemanticSourceMapEntry> lastValidSourceMap,
        CanonicalModelDocument lastCommittedSemanticModel,long lastCommittedRevision,
        List<TextualModelDiagnostic> diagnostics,Instant updatedAt,Map<String,String> metadata){
    public TextualModelSession{
        id=text(id);editorDefinitionId=text(editorDefinitionId);grammarId=text(grammarId);grammarSnapshotId=text(grammarSnapshotId);
        documents=documents==null?List.of():List.copyOf(documents);draftRevision=Math.max(0,draftRevision);lastParsedRevision=Math.max(0,lastParsedRevision);
        lastParsedSourceHash=text(lastParsedSourceHash);lastValidSourceMap=lastValidSourceMap==null?List.of():List.copyOf(lastValidSourceMap);
        lastCommittedRevision=Math.max(0,lastCommittedRevision);
        diagnostics=diagnostics==null?List.of():List.copyOf(diagnostics);updatedAt=updatedAt==null?Instant.now():updatedAt;
        metadata=metadata==null?Map.of():Map.copyOf(metadata);
    }
    /** Descripción: Ejecuta la operación «text» correspondiente a la responsabilidad de TextualModelSession. Entradas: v: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String text(String v){return v==null?"":v.trim();}
    /** Descripción: Ejecuta la operación «draftValid» correspondiente a la responsabilidad de TextualModelSession. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean draftValid(){return diagnostics.stream().noneMatch(d->d.severity()==com.example.editor.grammar.core.GrammarSeverity.ERROR);}
    /** Descripción: Evalúa la condición «hasLastKnownGood» sin modificar el estado externo. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean hasLastKnownGood(){return lastValidSemanticModel!=null;}
    /** Descripción: Evalúa la condición «hasCommittedSemanticModel» sin modificar el estado externo. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean hasCommittedSemanticModel(){return lastCommittedSemanticModel!=null;}
}
