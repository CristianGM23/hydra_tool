package com.example.editor.textual.definition;
import java.util.*;
/** Configurable role-based syntax style. Values are semantic theme tokens, not CSS/JS. */
public record TextualSyntaxStyleDefinition(String schemaVersion,String id,String name,Map<TextualSyntaxRole,String> roleTokens,Map<String,String> editorOptions){
    public TextualSyntaxStyleDefinition{schemaVersion=text(schemaVersion);if(schemaVersion.isBlank())schemaVersion="1.0";id=text(id);name=text(name);roleTokens=roleTokens==null?Map.of():Map.copyOf(roleTokens);editorOptions=editorOptions==null?Map.of():sanitize(editorOptions);}
    /** Descripción: Ejecuta la operación «sanitize» correspondiente a la responsabilidad de TextualSyntaxStyleDefinition. Entradas: values: colección de elementos que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static Map<String,String> sanitize(Map<String,String> values){Map<String,String> result=new LinkedHashMap<>();values.forEach((k,v)->{String key=text(k),value=text(v);if(key.matches("[A-Za-z][A-Za-z0-9._-]{0,63}")&&!value.contains("<script")&&!value.contains("javascript:")&&!value.contains("{")&&!value.contains("}"))result.put(key,value);});return Map.copyOf(result);}
    /** Descripción: Ejecuta la operación «text» correspondiente a la responsabilidad de TextualSyntaxStyleDefinition. Entradas: v: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String text(String v){return v==null?"":v.trim();}
}
