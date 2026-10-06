package com.example.editor.textual.runtime;

import java.util.List;

/** Atomic result of projecting and applying a shared ActionModel quick fix to text. */
public record TextualQuickFixApplicationResult(String diagnosticId,String actionId,boolean applied,boolean resolved,
        String reason,TextualModelSession session,String etag,List<TextualReconciliationEdit> edits,List<String>messages) {
    public TextualQuickFixApplicationResult {
        diagnosticId=text(diagnosticId);actionId=text(actionId);reason=reason==null?"":reason;etag=text(etag);
        edits=edits==null?List.of():List.copyOf(edits);messages=messages==null?List.of():List.copyOf(messages);
    }
    /** Descripción: Ejecuta la operación «text» correspondiente a la responsabilidad de TextualQuickFixApplicationResult. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String text(String value){return value==null?"":value.trim();}
}
