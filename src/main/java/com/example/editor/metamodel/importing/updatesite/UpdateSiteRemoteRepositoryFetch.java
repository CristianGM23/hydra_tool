package com.example.editor.metamodel.importing.updatesite;

/** Evidencia de una descarga remota individual. */
public record UpdateSiteRemoteRepositoryFetch(String url, String virtualPath, long bytes,
        String sha256, boolean cacheHit, int depth) {
    public UpdateSiteRemoteRepositoryFetch {
        url = text(url); virtualPath = text(virtualPath); sha256 = text(sha256);
    }
    /** Descripción: Ejecuta la operación «text» correspondiente a la responsabilidad de UpdateSiteRemoteRepositoryFetch. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String text(String value) { return value == null ? "" : value.trim(); }
}
