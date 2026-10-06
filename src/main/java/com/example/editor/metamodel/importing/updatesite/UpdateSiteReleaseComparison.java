package com.example.editor.metamodel.importing.updatesite;

import java.util.List;

/** Compatibilidad p2 avanzada: repositorios compuestos, versiones y deltas. */
public record UpdateSiteReleaseComparison(List<UpdateSiteCompositeRepository> compositeRepositories,
        List<UpdateSiteVersionFamily> versionFamilies, List<UpdateSiteVersionDelta> deltas,
        int unresolvedCompositeChildren, List<String> diagnostics) {
    public UpdateSiteReleaseComparison {
        compositeRepositories = compositeRepositories == null ? List.of() : List.copyOf(compositeRepositories);
        versionFamilies = versionFamilies == null ? List.of() : List.copyOf(versionFamilies);
        deltas = deltas == null ? List.of() : List.copyOf(deltas);
        unresolvedCompositeChildren = Math.max(0, unresolvedCompositeChildren);
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }
    /** Descripción: Ejecuta la operación «empty» correspondiente a la responsabilidad de UpdateSiteReleaseComparison. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public static UpdateSiteReleaseComparison empty() { return new UpdateSiteReleaseComparison(List.of(), List.of(), List.of(), 0, List.of()); }
}
