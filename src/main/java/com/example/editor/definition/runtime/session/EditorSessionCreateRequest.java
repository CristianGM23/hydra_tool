package com.example.editor.definition.runtime.session;
import java.util.Map;
public record EditorSessionCreateRequest(String sessionId, String modelId, String modelName,
        String representationId, String rootClassId, Map<String,Object> rootAttributes) {
    public EditorSessionCreateRequest { rootAttributes = rootAttributes == null ? Map.of() : Map.copyOf(rootAttributes); }
}
