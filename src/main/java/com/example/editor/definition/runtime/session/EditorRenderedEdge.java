package com.example.editor.definition.runtime.session;
import java.util.Map;
import com.example.editor.definition.EditorEdgeLineType;
public record EditorRenderedEdge(String relationInstanceId, String relationId, String relationName,
        String mappingId, String sourceElementId, String targetElementId, EditorEdgeLineType lineType,
        String label, Map<String,String> styles) {
    public EditorRenderedEdge { styles = styles == null ? Map.of() : Map.copyOf(styles); }
}
