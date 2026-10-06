package com.example.editor.validation.action;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.example.editor.validation.model.CanonicalModelDocument;

/** Entrada neutral para ejecutar una acción sobre un documento canónico. */
public record ActionExecutionRequest(CanonicalModelDocument document, String selfElementId, List<String> selectionElementIds, Map<String, Object> arguments) {
    public ActionExecutionRequest {
        if(document == null) throw new IllegalArgumentException("document must not be null");
        selfElementId = selfElementId == null ? "" : selfElementId;
        selectionElementIds = selectionElementIds == null ? List.of() : List.copyOf(selectionElementIds);
        arguments = arguments == null || arguments.isEmpty() ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(arguments));
    }
}
