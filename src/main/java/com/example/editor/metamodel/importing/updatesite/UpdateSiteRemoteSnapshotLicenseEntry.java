package com.example.editor.metamodel.importing.updatesite;

/** Per-resource provenance and redistribution decision required before exporting a p2 snapshot. */
public record UpdateSiteRemoteSnapshotLicenseEntry(String virtualPath, String author, String origin,
        String license, String obligations, boolean compatible, boolean redistributionAllowed) {
    public UpdateSiteRemoteSnapshotLicenseEntry {
        virtualPath = text(virtualPath);
        author = text(author);
        origin = text(origin);
        license = text(license);
        obligations = text(obligations);
    }

    /** Descripción: Ejecuta la operación «complete» correspondiente a la responsabilidad de UpdateSiteRemoteSnapshotLicenseEntry. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean complete() {
        return !virtualPath.isBlank() && !author.isBlank() && !origin.isBlank() && !license.isBlank()
                && !obligations.isBlank() && compatible && redistributionAllowed;
    }

    /** Descripción: Ejecuta la operación «text» correspondiente a la responsabilidad de UpdateSiteRemoteSnapshotLicenseEntry. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
