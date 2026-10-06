package com.example.editor.metamodel.importing.updatesite;

import java.util.List;

/** Resultado de recuperar comandos, menús, formularios y extensiones del editor generado. */
public record UpdateSiteSemanticRecovery(List<UpdateSiteSemanticElementDraft> elements,
        int representableCount, int partialCount, int unrepresentableCount, List<String> diagnostics) {
    public UpdateSiteSemanticRecovery {
        elements = elements == null ? List.of() : List.copyOf(elements);
        representableCount = Math.max(0, representableCount);
        partialCount = Math.max(0, partialCount);
        unrepresentableCount = Math.max(0, unrepresentableCount);
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }
    /** Descripción: Ejecuta la operación «empty» correspondiente a la responsabilidad de UpdateSiteSemanticRecovery. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public static UpdateSiteSemanticRecovery empty() { return new UpdateSiteSemanticRecovery(List.of(), 0, 0, 0, List.of()); }
}
