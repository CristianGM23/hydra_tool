package com.example.editor.textual.source;

import java.util.Map;
import com.example.editor.grammar.core.TextRange;

/** Bidirectional trace between textual ranges and the shared canonical semantic model. */
public record TextualSemanticSourceMapEntry(
        String semanticId,
        String semanticKind,
        String documentId,
        TextRange range,
        String metamodelElementId,
        String grammarRuleId,
        Map<String,String> metadata) {
    public TextualSemanticSourceMapEntry {
        semanticId=text(semanticId); semanticKind=text(semanticKind); documentId=text(documentId);
        range=range==null?new TextRange(null,null):range;
        metamodelElementId=text(metamodelElementId); grammarRuleId=text(grammarRuleId);
        metadata=metadata==null?Map.of():Map.copyOf(metadata);
    }
    /** Compatibility constructor retained for V4.32.97 early drafts. */
    /** Descripción: Inicializa una instancia de TextualSemanticSourceMapEntry con sus dependencias y datos de entrada. Entradas: semanticId: identificador que delimita el recurso objetivo; semanticKind: dato de entrada necesario para la operación; documentId: identificador que delimita el recurso objetivo; range: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public TextualSemanticSourceMapEntry(String semanticId,String semanticKind,String documentId,TextRange range){
        this(semanticId,semanticKind,documentId,range,"","",Map.of());
    }
    /** Descripción: Ejecuta la operación «text» correspondiente a la responsabilidad de TextualSemanticSourceMapEntry. Entradas: v: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String text(String v){return v==null?"":v.trim();}
}
