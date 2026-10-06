package com.example.editor.integration.bridge;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
/** Resultado neutral de una acción o quick fix ejecutado por el host. */
public record HostActionResult(boolean success, String message, List<String> changedElementIds, Map<String, Object> outputs) {
    public HostActionResult {
        message = message == null ? "" : message.trim();
        changedElementIds = changedElementIds == null ? List.of() : List.copyOf(changedElementIds);
        outputs = outputs == null || outputs.isEmpty() ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(outputs));
    }
}
