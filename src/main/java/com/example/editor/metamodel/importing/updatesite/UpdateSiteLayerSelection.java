package com.example.editor.metamodel.importing.updatesite;

import java.util.List;

/** Selección granular de borradores recuperados para materialización. */
public record UpdateSiteLayerSelection(List<String> paletteGroupIds, List<String> paletteToolIds,
        List<String> representationIds, List<String> mappingIds, List<String> semanticElementIds,
        boolean explicit) {
    public UpdateSiteLayerSelection {
        paletteGroupIds = paletteGroupIds == null ? List.of() : List.copyOf(paletteGroupIds);
        paletteToolIds = paletteToolIds == null ? List.of() : List.copyOf(paletteToolIds);
        representationIds = representationIds == null ? List.of() : List.copyOf(representationIds);
        mappingIds = mappingIds == null ? List.of() : List.copyOf(mappingIds);
        semanticElementIds = semanticElementIds == null ? List.of() : List.copyOf(semanticElementIds);
    }

    /** Descripción: Ejecuta la operación «all» correspondiente a la responsabilidad de UpdateSiteLayerSelection. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public static UpdateSiteLayerSelection all() {
        return new UpdateSiteLayerSelection(List.of(), List.of(), List.of(), List.of(), List.of(), false);
    }
}
