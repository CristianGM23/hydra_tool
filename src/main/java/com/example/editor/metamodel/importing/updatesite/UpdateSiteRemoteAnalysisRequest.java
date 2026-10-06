package com.example.editor.metamodel.importing.updatesite;

/** Configuración explícita para resolver y analizar un repositorio p2 remoto. */
public record UpdateSiteRemoteAnalysisRequest(String repositoryUrl, String expectedSha256,
        int maximumDepth, boolean useCache, UpdateSiteImportOptions options) {
    public UpdateSiteRemoteAnalysisRequest {
        repositoryUrl = text(repositoryUrl);
        expectedSha256 = text(expectedSha256).toLowerCase();
        maximumDepth = Math.max(0, maximumDepth);
        options = options == null ? UpdateSiteImportOptions.defaults() : options;
    }
    /** Descripción: Ejecuta la operación «text» correspondiente a la responsabilidad de UpdateSiteRemoteAnalysisRequest. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String text(String value) { return value == null ? "" : value.trim(); }
}
