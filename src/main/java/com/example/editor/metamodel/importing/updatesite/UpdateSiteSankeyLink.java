package com.example.editor.metamodel.importing.updatesite;

/** Enlace cuantificado del flujo de importación. */
public record UpdateSiteSankeyLink(String source, String target, long value, String explanation) {
    public UpdateSiteSankeyLink {
        source = source == null ? "" : source;
        target = target == null ? "" : target;
        value = Math.max(0, value);
        explanation = explanation == null ? "" : explanation;
    }
}
