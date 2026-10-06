package com.example.editor.definition.runtime.session;

import java.util.List;

/** Selección estable de elementos de una sesión para operaciones atómicas por lote. */
public record EditorElementSelectionRequest(List<String> elementIds) {
    public EditorElementSelectionRequest { elementIds = elementIds == null ? List.of() : elementIds.stream().filter(id -> id != null && !id.isBlank()).distinct().toList(); }
}
