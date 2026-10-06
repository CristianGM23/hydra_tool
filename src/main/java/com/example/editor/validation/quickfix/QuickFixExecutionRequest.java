package com.example.editor.validation.quickfix;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Parámetros elegidos para previsualizar o aplicar un quick fix. */
public record QuickFixExecutionRequest(Map<String, Object> arguments, List<String> selectionElementIds) {
    public QuickFixExecutionRequest {
        arguments = arguments == null || arguments.isEmpty() ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(arguments));
        selectionElementIds = selectionElementIds == null ? List.of() : selectionElementIds.stream().filter(value -> value != null && !value.isBlank()).distinct().toList();
    }
}
