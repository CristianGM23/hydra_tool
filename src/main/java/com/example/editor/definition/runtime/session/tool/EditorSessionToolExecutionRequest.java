package com.example.editor.definition.runtime.session.tool;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Entrada neutral para ejecutar una herramienta compilada sobre una sesión de editor. */
public record EditorSessionToolExecutionRequest(String representationId, String targetElementId,
        List<String> selectionElementIds, Map<String, Object> arguments) {
    public EditorSessionToolExecutionRequest {
        representationId = representationId == null ? "" : representationId.trim();
        targetElementId = targetElementId == null ? "" : targetElementId.trim();
        selectionElementIds = selectionElementIds == null ? List.of()
                : selectionElementIds.stream().filter(value -> value != null && !value.isBlank()).distinct().toList();
        arguments = arguments == null || arguments.isEmpty() ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(arguments));
    }
}
