package com.example.editor.definition.runtime.session;

import com.example.editor.definition.EditorNodeShape;

/** Apariencia visual compatible que puede elegir una instancia del modelo. */
public record EditorNodeAppearanceView(String mappingId, String name, EditorNodeShape shape, String imageAssetPath) {
    public EditorNodeAppearanceView {
        mappingId = mappingId == null ? "" : mappingId;
        name = name == null || name.isBlank() ? mappingId : name;
        imageAssetPath = imageAssetPath == null ? "" : imageAssetPath;
    }
}
