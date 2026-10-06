package com.example.editor.definition.runtime.session;

import com.example.editor.definition.RepresentationKind;

/** Resumen de una representación que puede abrirse dentro de una sesión neutral. */
public record EditorRepresentationRuntimeView(String id, String name, RepresentationKind kind,
        boolean editable, boolean active, boolean open) {
    public EditorRepresentationRuntimeView {
        id = id == null ? "" : id;
        name = name == null ? id : name;
    }
}
