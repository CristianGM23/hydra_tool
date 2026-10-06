package com.example.editor.definition.importing;

/** Explains which semantic metamodel was used while analysing an imported visual representation. */
public record VisualMetamodelAssociation(String mode, String sourceName, boolean alreadyPersisted, String detectedFormat,
        String explanation) {
    public VisualMetamodelAssociation {
        mode = mode == null || mode.isBlank() ? "EXISTING" : mode;
        sourceName = sourceName == null ? "" : sourceName;
        detectedFormat = detectedFormat == null ? "" : detectedFormat;
        explanation = explanation == null ? "" : explanation;
    }
}
