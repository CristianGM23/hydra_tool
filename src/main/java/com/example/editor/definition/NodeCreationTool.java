package com.example.editor.definition;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
/** Herramienta que crea una instancia de una clase del metamodelo. */
public record NodeCreationTool(String id, String label, String description, String icon, List<String> representationIds,
        String visibleExpressionId, String enabledExpressionId, String targetClassId, Map<String, Object> defaultValues, String formId) implements EditorTool {
    public NodeCreationTool {
        id = EditorToolSupport.id(id, "tool.id");
        label = EditorToolSupport.text(label, id);
        description = EditorToolSupport.text(description, "");
        icon = EditorToolSupport.text(icon, "");
        representationIds = EditorToolSupport.ids(representationIds, "representationIds");
        visibleExpressionId = EditorToolSupport.optionalId(visibleExpressionId, "visibleExpressionId");
        enabledExpressionId = EditorToolSupport.optionalId(enabledExpressionId, "enabledExpressionId");
        targetClassId = EditorToolSupport.id(targetClassId, "targetClassId");
        defaultValues = defaultValues == null || defaultValues.isEmpty() ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(defaultValues));
        formId = EditorToolSupport.optionalId(formId, "formId");
    }
}
