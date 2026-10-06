package com.example.editor.metamodel.export.project;

import java.util.Map;

/** Describe una exportación completa y sus valores configurables por defecto. */
public record MetamodelProjectExportDescriptor(String id, String displayName, String description, String technology,
        boolean requiresEditorDefinition, boolean validationRequired, String extension, Map<String, String> defaultSettings) {
    public MetamodelProjectExportDescriptor {defaultSettings = defaultSettings == null ? Map.of() : Map.copyOf(defaultSettings);}
}
