package com.example.editor.definition;
import java.util.List;
import com.example.editor.validation.contract.StableIdentifierPolicy;
/** Grupo ordenado de herramientas de un editor. */
public record PaletteGroup(String id, String name, int order, List<String> toolIds, boolean initiallyCollapsed) {
    public PaletteGroup {
        id = StableIdentifierPolicy.requireElementId(id, "paletteGroup.id");
        name = name == null || name.isBlank() ? id : name.trim();
        toolIds = toolIds == null ? List.of() : toolIds.stream().map(value -> StableIdentifierPolicy.requireElementId(value, "toolIds")).toList();
    }
}
