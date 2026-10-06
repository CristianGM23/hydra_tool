package com.example.editor.metamodel.importing.updatesite;

import java.util.List;

/** Bounded declarative fuzzing and invariant request for one adapter version. */
public record UpdateSiteAdapterVerificationRequest(String adapterId, String version,
        String document, int fuzzCases, List<String> invariants) {
    public UpdateSiteAdapterVerificationRequest {
        adapterId = text(adapterId); version = text(version);
        document = document == null ? "" : document;
        fuzzCases = Math.max(0, Math.min(256, fuzzCases));
        invariants = invariants == null ? List.of() : invariants.stream().map(UpdateSiteAdapterVerificationRequest::text)
                .filter(value -> !value.isBlank()).distinct().toList();
    }
    /** Descripción: Ejecuta la operación «text» correspondiente a la responsabilidad de UpdateSiteAdapterVerificationRequest. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String text(String value){return value == null ? "" : value.trim();}
}
