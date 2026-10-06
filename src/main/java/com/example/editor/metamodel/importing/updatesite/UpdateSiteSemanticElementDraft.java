package com.example.editor.metamodel.importing.updatesite;

import java.util.List;

/** Borrador semántico recuperado de un editor Eclipse generado. */
public record UpdateSiteSemanticElementDraft(String id, UpdateSiteSemanticKind kind, String label,
        String implementation, String sourcePath, String extensionPoint, UpdateSiteConfidence confidence,
        boolean representable, String neutralTarget, String purpose, List<String> warnings) {
    public UpdateSiteSemanticElementDraft {
        id = text(id);
        kind = kind == null ? UpdateSiteSemanticKind.UNREPRESENTABLE : kind;
        label = text(label);
        implementation = text(implementation);
        sourcePath = text(sourcePath);
        extensionPoint = text(extensionPoint);
        confidence = confidence == null ? UpdateSiteConfidence.RECOVERED : confidence;
        neutralTarget = text(neutralTarget);
        purpose = text(purpose);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
    /** Descripción: Ejecuta la operación «text» correspondiente a la responsabilidad de UpdateSiteSemanticElementDraft. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String text(String value) { return value == null ? "" : value.trim(); }
}
