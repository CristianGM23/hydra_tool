package com.example.editor.metamodel.importing.updatesite;

/** Nodo del flujo compacto de descubrimiento y recuperación. */
public record UpdateSiteSankeyNode(String id, String label, int column, String explanation) {
    public UpdateSiteSankeyNode {
        id = id == null ? "" : id;
        label = label == null ? id : label;
        column = Math.max(0, column);
        explanation = explanation == null ? "" : explanation;
    }
}
