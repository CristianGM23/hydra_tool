package com.example.editor.model;

import java.util.List;

/** Solicitud de mutación para uno o varios elementos del modelo. */
public record ModelElementSelectionRequest(List<String> elementIds) {
    public ModelElementSelectionRequest {
        elementIds = elementIds == null ? List.of() : elementIds.stream().filter(id -> id != null && !id.isBlank()).distinct().toList();
    }
}
