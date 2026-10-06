package com.example.editor.definition.runtime.session;
import java.util.Map;
public record EditorElementUpdateRequest(String representationId, String formId, Map<String,Object> values) {
    public EditorElementUpdateRequest { values = values == null ? Map.of() : Map.copyOf(values); }
}
