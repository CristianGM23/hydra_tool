package com.example.editor.definition;
import java.util.List;
/** Herramienta que ejecuta una ActionModel. */
public record ActionEditorTool(String id, String label, String description, String icon, List<String> representationIds,
        String visibleExpressionId, String enabledExpressionId, String actionId) implements EditorTool {
    public ActionEditorTool {
        id = EditorToolSupport.id(id, "tool.id"); label = EditorToolSupport.text(label, id); description = EditorToolSupport.text(description, ""); icon = EditorToolSupport.text(icon, "");
        representationIds = EditorToolSupport.ids(representationIds, "representationIds"); visibleExpressionId = EditorToolSupport.optionalId(visibleExpressionId, "visibleExpressionId"); enabledExpressionId = EditorToolSupport.optionalId(enabledExpressionId, "enabledExpressionId"); actionId = EditorToolSupport.id(actionId, "actionId");
    }
}
