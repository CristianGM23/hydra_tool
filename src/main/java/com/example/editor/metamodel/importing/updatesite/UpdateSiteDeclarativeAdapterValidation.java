package com.example.editor.metamodel.importing.updatesite;

import java.util.List;

/** Resultado de validar un adaptador declarativo antes de registrarlo. */
public record UpdateSiteDeclarativeAdapterValidation(boolean valid,
        UpdateSiteDeclarativeAdapterDocument document, String fingerprint,
        List<String> errors, List<String> warnings) {
    public UpdateSiteDeclarativeAdapterValidation {
        fingerprint = fingerprint == null ? "" : fingerprint.trim();
        errors = errors == null ? List.of() : List.copyOf(errors);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
