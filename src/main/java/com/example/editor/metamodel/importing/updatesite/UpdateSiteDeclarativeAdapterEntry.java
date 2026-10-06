package com.example.editor.metamodel.importing.updatesite;

import java.util.List;

/** Persisted declarative adapter metadata for visual authoring and version history. */
public record UpdateSiteDeclarativeAdapterEntry(UpdateSiteDeclarativeAdapterDocument document,
        String fileName, String fingerprint, String version, List<String> availableVersions) {
    public UpdateSiteDeclarativeAdapterEntry {
        fileName = fileName == null ? "" : fileName;
        fingerprint = fingerprint == null ? "" : fingerprint;
        version = version == null || version.isBlank() ? "1.0.0" : version;
        availableVersions = availableVersions == null ? List.of() : List.copyOf(availableVersions);
    }

    /** Descripción: Ejecuta la operación «versionOf» correspondiente a la responsabilidad de UpdateSiteDeclarativeAdapterEntry. Entradas: document: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String versionOf(UpdateSiteDeclarativeAdapterDocument document) {
        if(document == null) return "1.0.0";
        String value = document.annotations().getOrDefault("version", "1.0.0");
        return value == null || value.isBlank() ? "1.0.0" : value;
    }
}
