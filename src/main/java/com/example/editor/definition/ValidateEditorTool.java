package com.example.editor.definition;
import java.util.List;
/** Herramienta que solicita una modalidad de validación. */
public record ValidateEditorTool(String id, String label, String description, String icon, List<String> representationIds,
        String visibleExpressionId, String enabledExpressionId, String validationMode) implements EditorTool {
    public ValidateEditorTool {
        id = EditorToolSupport.id(id, "tool.id"); label = EditorToolSupport.text(label, id); description = EditorToolSupport.text(description, ""); icon = EditorToolSupport.text(icon, "");
        representationIds = EditorToolSupport.ids(representationIds, "representationIds"); visibleExpressionId = EditorToolSupport.optionalId(visibleExpressionId, "visibleExpressionId"); enabledExpressionId = EditorToolSupport.optionalId(enabledExpressionId, "enabledExpressionId"); validationMode = EditorToolSupport.text(validationMode, "MANUAL");
    }
}
