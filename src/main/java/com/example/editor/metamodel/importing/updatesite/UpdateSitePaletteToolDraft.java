package com.example.editor.metamodel.importing.updatesite;

/** Herramienta de paleta reconstruida desde GMF, Sirius o una gramática. */
public record UpdateSitePaletteToolDraft(String id, String label, String kind, String targetElement,
        String sourcePath, UpdateSiteConfidence confidence) {
    public UpdateSitePaletteToolDraft {
        id = id == null ? "" : id;
        label = label == null || label.isBlank() ? id : label;
        kind = kind == null ? "NODE" : kind;
        targetElement = targetElement == null ? "" : targetElement;
        sourcePath = sourcePath == null ? "" : sourcePath;
        confidence = confidence == null ? UpdateSiteConfidence.DERIVED : confidence;
    }
}
