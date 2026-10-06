package com.example.editor.definition;
import java.util.List;
import java.util.Optional;
/** Paleta configurable perteneciente a un editor de modelos concreto. */
public record ToolPaletteDefinition(List<PaletteGroup> groups, List<EditorTool> tools) {
    public ToolPaletteDefinition { groups = groups == null ? List.of() : List.copyOf(groups); tools = tools == null ? List.of() : List.copyOf(tools); }
    /** Descripción: Obtiene la información correspondiente a «findTool» dentro de ToolPaletteDefinition. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public Optional<EditorTool> findTool(String id) { return tools.stream().filter(tool -> tool.id().equals(id)).findFirst(); }
}
