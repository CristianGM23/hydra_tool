package com.example.editor.metamodel.importing.updatesite;

import java.util.List;

/** Runs a stored or inline declarative adapter against a deterministic fixture suite. */
public record UpdateSiteAdapterBenchRequest(String adapterId, String version, String document,
        List<UpdateSiteAdapterBenchCase> fixtures, boolean includeMutations) {
    public UpdateSiteAdapterBenchRequest {
        adapterId = text(adapterId); version = text(version); document = document == null ? "" : document;
        fixtures = fixtures == null ? List.of() : List.copyOf(fixtures);
    }
    /** Descripción: Ejecuta la operación «text» correspondiente a la responsabilidad de UpdateSiteAdapterBenchRequest. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String text(String value){return value == null ? "" : value.trim();}
}
