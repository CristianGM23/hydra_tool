package com.example.editor.textual.runtime;

import java.util.List;
import com.example.editor.textual.source.TextualModelSourceDocument;
import com.example.editor.validation.model.CanonicalModelDocument;
import com.example.editor.validation.quickfix.CanonicalModelChangeSummary;

/** Safe textual preview of an existing HYDRA ActionModel quick fix. */
public record TextualQuickFixPreviewResult(String diagnosticId,String actionId,CanonicalModelDocument candidateModel,
        CanonicalModelChangeSummary changes,boolean reconciliationSafe,String reason,List<TextualReconciliationEdit> edits,
        List<TextualModelSourceDocument> candidateDocuments,List<String> messages) {
    public TextualQuickFixPreviewResult {
        diagnosticId=text(diagnosticId);actionId=text(actionId);reason=reason==null?"":reason;
        edits=edits==null?List.of():List.copyOf(edits);candidateDocuments=candidateDocuments==null?List.of():List.copyOf(candidateDocuments);
        messages=messages==null?List.of():List.copyOf(messages);
    }
    /** Descripción: Ejecuta la operación «text» correspondiente a la responsabilidad de TextualQuickFixPreviewResult. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String text(String value){return value==null?"":value.trim();}
}
