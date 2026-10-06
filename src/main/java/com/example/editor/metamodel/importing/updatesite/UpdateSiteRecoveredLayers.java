package com.example.editor.metamodel.importing.updatesite;

import java.util.List;

/** Capas neutrales derivadas de artefactos GMF, Sirius y Xtext. */
public record UpdateSiteRecoveredLayers(List<UpdateSitePaletteGroupDraft> palettes,
        List<UpdateSiteRepresentationDraft> representations, List<UpdateSiteMappingDraft> mappings,
        List<String> diagnostics) {
    public UpdateSiteRecoveredLayers {
        palettes = palettes == null ? List.of() : List.copyOf(palettes);
        representations = representations == null ? List.of() : List.copyOf(representations);
        mappings = mappings == null ? List.of() : List.copyOf(mappings);
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }

    /** Descripción: Ejecuta la operación «empty» correspondiente a la responsabilidad de UpdateSiteRecoveredLayers. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public static UpdateSiteRecoveredLayers empty() {
        return new UpdateSiteRecoveredLayers(List.of(), List.of(), List.of(), List.of());
    }
}
