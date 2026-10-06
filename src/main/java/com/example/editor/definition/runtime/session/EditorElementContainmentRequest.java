package com.example.editor.definition.runtime.session;

/** Changes the semantic containment of an existing model element after a visual drag-and-drop. */
public record EditorElementContainmentRequest(String representationId, String containerElementId,
        String containmentRelationId) { }
