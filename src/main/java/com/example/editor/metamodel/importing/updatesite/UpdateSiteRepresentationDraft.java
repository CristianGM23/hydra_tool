package com.example.editor.metamodel.importing.updatesite;

import java.util.List;

/** Representación neutral recuperada desde GMF o Sirius. */
public record UpdateSiteRepresentationDraft(String id, String name, String technology, String kind,
        List<String> rootElements, String sourcePath, UpdateSiteConfidence confidence, List<String> warnings) {
    public UpdateSiteRepresentationDraft {
        id = id == null ? "" : id;
        name = name == null || name.isBlank() ? id : name;
        technology = technology == null ? "" : technology;
        kind = kind == null ? "DIAGRAM" : kind;
        rootElements = rootElements == null ? List.of() : List.copyOf(rootElements);
        sourcePath = sourcePath == null ? "" : sourcePath;
        confidence = confidence == null ? UpdateSiteConfidence.DERIVED : confidence;
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
