package com.example.editor.metamodel.importing.updatesite;

import java.util.List;

/** Trazabilidad de un elemento neutral recuperado. */
public record UpdateSiteElementProvenance(String elementId, String elementPath, String origin,
        String sourcePath, String format, String originalElement, UpdateSiteRecoveryMethod method,
        UpdateSiteConfidence confidence, List<String> warnings) {
    public UpdateSiteElementProvenance {
        elementId = elementId == null ? "" : elementId;
        elementPath = elementPath == null ? "" : elementPath;
        origin = origin == null ? "" : origin;
        sourcePath = sourcePath == null ? "" : sourcePath;
        format = format == null ? "" : format;
        originalElement = originalElement == null ? "" : originalElement;
        method = method == null ? UpdateSiteRecoveryMethod.CODE_INFERENCE : method;
        confidence = confidence == null ? UpdateSiteConfidence.INFERRED : confidence;
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
