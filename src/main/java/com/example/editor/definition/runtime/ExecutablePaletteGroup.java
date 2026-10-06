package com.example.editor.definition.runtime;

import java.util.List;

import com.example.editor.definition.EditorTool;
import com.fasterxml.jackson.annotation.JsonInclude;

/** Grupo de herramientas ya filtrado para una representación ejecutable. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ExecutablePaletteGroup(String id, String name, int order, boolean initiallyCollapsed,
        List<EditorTool> tools) {

    public ExecutablePaletteGroup {
        tools = tools == null ? List.of() : List.copyOf(tools);
    }
}
