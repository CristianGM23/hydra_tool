package com.example.editor.textual.source;

import java.util.*;
import com.example.editor.grammar.core.TextPosition;
import com.example.editor.grammar.core.TextRange;

/** Immutable indexed source map. Index construction is O(n); semantic/range lookup avoids scanning unrelated documents. */
public final class TextualSemanticSourceMap {
    private final List<TextualSemanticSourceMapEntry> entries;
    private final Map<String,List<TextualSemanticSourceMapEntry>> bySemanticId;
    private final Map<String,List<TextualSemanticSourceMapEntry>> byDocument;

    /** Descripción: Inicializa una instancia de TextualSemanticSourceMap con sus dependencias y datos de entrada. Entradas: entries: colección de elementos que debe procesarse. Salida: deja la instancia inicializada y lista para su uso. */ public TextualSemanticSourceMap(List<TextualSemanticSourceMapEntry> entries){
        this.entries=entries==null?List.of():List.copyOf(entries);
        Map<String,List<TextualSemanticSourceMapEntry>> semantic=new HashMap<>(),documents=new HashMap<>();
        for(var entry:this.entries){
            semantic.computeIfAbsent(entry.semanticId(),ignored->new ArrayList<>()).add(entry);
            documents.computeIfAbsent(entry.documentId(),ignored->new ArrayList<>()).add(entry);
        }
        semantic.replaceAll((k,v)->List.copyOf(v));
        documents.replaceAll((k,v)->v.stream().sorted(Comparator.comparingInt(e->e.range().start().line()*1_000_000+e.range().start().column())).toList());
        this.bySemanticId=Map.copyOf(semantic);this.byDocument=Map.copyOf(documents);
    }
    /** Descripción: Ejecuta la operación «entries» correspondiente a la responsabilidad de TextualSemanticSourceMap. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public List<TextualSemanticSourceMapEntry> entries(){return entries;}
    /** Descripción: Ejecuta la operación «forSemanticId» correspondiente a la responsabilidad de TextualSemanticSourceMap. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public List<TextualSemanticSourceMapEntry> forSemanticId(String id){return bySemanticId.getOrDefault(id,List.of());}
    /** Descripción: Ejecuta la operación «at» correspondiente a la responsabilidad de TextualSemanticSourceMap. Entradas: documentId: identificador que delimita el recurso objetivo; position: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public Optional<TextualSemanticSourceMapEntry> at(String documentId,TextPosition position){
        if(position==null)return Optional.empty();
        return byDocument.getOrDefault(documentId,List.of()).stream().filter(e->contains(e.range(),position))
                .min(Comparator.comparingInt(TextualSemanticSourceMap::span));
    }
    /** Descripción: Evalúa la condición «contains» sin modificar el estado externo. Entradas: r: dato de entrada necesario para la operación; p: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private static boolean contains(TextRange r,TextPosition p){
        int afterStart=Integer.compare(p.line(),r.start().line()); if(afterStart<0)return false;
        if(afterStart==0&&p.column()<r.start().column())return false;
        int beforeEnd=Integer.compare(p.line(),r.end().line()); if(beforeEnd>0)return false;
        return beforeEnd!=0||p.column()<=r.end().column();
    }
    /** Descripción: Ejecuta la operación «span» correspondiente a la responsabilidad de TextualSemanticSourceMap. Entradas: e: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static int span(TextualSemanticSourceMapEntry e){return Math.max(0,(e.range().end().line()-e.range().start().line())*1_000_000+e.range().end().column()-e.range().start().column());}
}
