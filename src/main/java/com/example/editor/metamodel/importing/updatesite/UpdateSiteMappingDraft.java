package com.example.editor.metamodel.importing.updatesite;

/** Mapping neutral entre un elemento semántico y su representación o herramienta. */
public record UpdateSiteMappingDraft(String id, String semanticElement, String visualElement,
        String mappingKind, String sourcePath, UpdateSiteConfidence confidence, String diagnostic) {
    public UpdateSiteMappingDraft {
        id = id == null ? "" : id;
        semanticElement = semanticElement == null ? "" : semanticElement;
        visualElement = visualElement == null ? "" : visualElement;
        mappingKind = mappingKind == null ? "NODE" : mappingKind;
        sourcePath = sourcePath == null ? "" : sourcePath;
        confidence = confidence == null ? UpdateSiteConfidence.DERIVED : confidence;
        diagnostic = diagnostic == null ? "" : diagnostic;
    }
}
