package com.example.editor.definition;
import java.util.List;
/** Herramienta que expone una operación del metamodelo. */
public record OperationEditorTool(String id, String label, String description, String icon, List<String> representationIds,
        String visibleExpressionId, String enabledExpressionId, String operationId) implements EditorTool {
    public OperationEditorTool {
        id = EditorToolSupport.id(id, "tool.id"); label = EditorToolSupport.text(label, id); description = EditorToolSupport.text(description, ""); icon = EditorToolSupport.text(icon, "");
        representationIds = EditorToolSupport.ids(representationIds, "representationIds"); visibleExpressionId = EditorToolSupport.optionalId(visibleExpressionId, "visibleExpressionId"); enabledExpressionId = EditorToolSupport.optionalId(enabledExpressionId, "enabledExpressionId"); operationId = EditorToolSupport.id(operationId, "operationId");
    }
}
