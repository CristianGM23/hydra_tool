package com.example.editor.metamodel.importing.updatesite;

/** Hijo declarado por un repositorio compuesto p2. */
public record UpdateSiteCompositeRepository(String sourcePath, String location, boolean remote,
        boolean presentInUpload, String explanation) {
    public UpdateSiteCompositeRepository {
        sourcePath = text(sourcePath);
        location = text(location);
        explanation = text(explanation);
    }
    /** Descripción: Ejecuta la operación «text» correspondiente a la responsabilidad de UpdateSiteCompositeRepository. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String text(String value) { return value == null ? "" : value.trim(); }
}
