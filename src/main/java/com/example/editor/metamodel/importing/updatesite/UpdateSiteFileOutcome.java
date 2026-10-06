package com.example.editor.metamodel.importing.updatesite;

import java.util.List;

/** Explicación orientada al usuario de qué es un archivo y qué pudo recuperar HYDRA. */
public record UpdateSiteFileOutcome(String sourcePath, String artifactKind, String purpose,
        UpdateSiteFileStatus status, int recoveredItems, String recoveredAs, String explanation,
        String nextAction, List<String> relatedElements) {
    public UpdateSiteFileOutcome {
        sourcePath = value(sourcePath);
        artifactKind = value(artifactKind);
        purpose = value(purpose);
        status = status == null ? UpdateSiteFileStatus.SUPPORTING : status;
        recoveredItems = Math.max(0, recoveredItems);
        recoveredAs = value(recoveredAs);
        explanation = value(explanation);
        nextAction = value(nextAction);
        relatedElements = relatedElements == null ? List.of() : List.copyOf(relatedElements);
    }
    /** Descripción: Ejecuta la operación «value» correspondiente a la responsabilidad de UpdateSiteFileOutcome. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String value(String value) { return value == null ? "" : value.trim(); }
}
