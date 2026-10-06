package com.example.editor.operation.runtime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
public record OperationInvocationRequest(String targetElementId, Map<String, Object> arguments, List<String> selectionElementIds) {
    public OperationInvocationRequest { targetElementId = targetElementId == null ? "" : targetElementId; arguments = arguments == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(arguments)); selectionElementIds = selectionElementIds == null ? List.of() : List.copyOf(selectionElementIds); }
}
