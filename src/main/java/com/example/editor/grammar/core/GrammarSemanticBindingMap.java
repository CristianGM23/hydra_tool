package com.example.editor.grammar.core;
import java.util.List;
import java.util.Map;
public record GrammarSemanticBindingMap(String schemaVersion, List<GrammarSemanticBinding> bindings, Map<String,String> annotations) {
    public GrammarSemanticBindingMap { schemaVersion=text(schemaVersion); if(schemaVersion.isEmpty()) schemaVersion="1.0"; bindings=bindings==null?List.of():List.copyOf(bindings); annotations=annotations==null?Map.of():Map.copyOf(annotations); }
    /** Descripción: Ejecuta la operación «empty» correspondiente a la responsabilidad de GrammarSemanticBindingMap. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public static GrammarSemanticBindingMap empty(){return new GrammarSemanticBindingMap("1.0",List.of(),Map.of());}
    /** Descripción: Ejecuta la operación «text» correspondiente a la responsabilidad de GrammarSemanticBindingMap. Entradas: v: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String text(String v){return v==null?"":v.trim();}
}
