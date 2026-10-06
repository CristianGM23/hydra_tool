package com.example.editor.integration.roundtrip;

import java.util.List;
import java.util.Map;
import com.example.editor.integration.IntegrationTechnology;

/** Preview sin escritura de una sincronización incremental. */
public record RoundTripSyncPreview(IntegrationTechnology technology, String profileId,
        List<RoundTripFileChange> changes, List<String> conflicts,
        List<String> selectablePaths, Map<String, String> metadata) {
    public RoundTripSyncPreview {
        if(technology == null){throw new IllegalArgumentException("technology is required");}
        profileId = profileId == null ? "" : profileId.trim();
        changes = changes == null ? List.of() : List.copyOf(changes);
        conflicts = conflicts == null ? List.of() : List.copyOf(conflicts);
        selectablePaths = selectablePaths == null ? List.of() : List.copyOf(selectablePaths);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
    /** Descripción: Ejecuta la operación «blocked» correspondiente a la responsabilidad de RoundTripSyncPreview. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean blocked(){return !conflicts.isEmpty();}
}
