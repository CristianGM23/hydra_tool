package com.example.editor.textual.runtime;

import java.util.List;
import java.util.Map;
import com.example.editor.textual.source.TextualSemanticSourceMapEntry;
import com.example.editor.validation.model.CanonicalModelDocument;

public record TextualParseResult(long revision,String sourceHash,boolean valid,CanonicalModelDocument semanticModel,
        List<TextualSemanticSourceMapEntry> sourceMap,List<TextualModelDiagnostic> diagnostics,String grammarSnapshotId,Map<String,String> metadata){
    public TextualParseResult{sourceHash=sourceHash==null?"":sourceHash;sourceMap=sourceMap==null?List.of():List.copyOf(sourceMap);diagnostics=diagnostics==null?List.of():List.copyOf(diagnostics);grammarSnapshotId=grammarSnapshotId==null?"":grammarSnapshotId;metadata=metadata==null?Map.of():Map.copyOf(metadata);}
}
