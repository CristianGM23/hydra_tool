package com.example.editor.textual.runtime;

import java.util.List;
import java.util.Map;
import com.example.editor.grammar.core.TextRange;
import com.example.editor.validation.runtime.ModelValidationDiagnostic;

/** Shared HYDRA validation diagnostic enriched with its textual location. */
public record TextualValidationDiagnosticView(ModelValidationDiagnostic diagnostic, String documentId,
        TextRange range, boolean textLocationAvailable, List<String> quickFixIds, Map<String,String> metadata) {
    public TextualValidationDiagnosticView {
        documentId=documentId==null?"":documentId;
        range=range==null?new TextRange(null,null):range;
        quickFixIds=quickFixIds==null?List.of():List.copyOf(quickFixIds);
        metadata=metadata==null?Map.of():Map.copyOf(metadata);
    }
}
