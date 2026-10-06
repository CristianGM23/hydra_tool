package com.example.editor.definition.runtime.session;
import java.util.Map;
public record EditorNodeCreationRequest(String representationId, String containerElementId,
        String containmentRelationId, Map<String,Object> values) {
    public EditorNodeCreationRequest { values = values == null ? Map.of() : Map.copyOf(values); }
}
