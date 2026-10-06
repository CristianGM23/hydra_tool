package com.example.editor.metamodel.importing.updatesite;

import java.util.List;

/** Grupo neutral de herramientas recuperado. */
public record UpdateSitePaletteGroupDraft(String id, String label, int order,
        List<UpdateSitePaletteToolDraft> tools, String sourcePath, UpdateSiteConfidence confidence) {
    public UpdateSitePaletteGroupDraft {
        id = id == null ? "" : id;
        label = label == null || label.isBlank() ? id : label;
        tools = tools == null ? List.of() : List.copyOf(tools);
        sourcePath = sourcePath == null ? "" : sourcePath;
        confidence = confidence == null ? UpdateSiteConfidence.DERIVED : confidence;
    }
}
