package com.example.editor.integration.roundtrip.resolution;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import com.example.editor.integration.IntegrationTechnology;
import com.example.editor.integration.roundtrip.RoundTripSyncPreview;

/** Immutable preview-to-apply plan. No write is allowed while unresolved conflicts remain. */
public record RoundTripResolutionPlan(String operationId, IntegrationTechnology technology, String profileId,
        Path targetRoot, RoundTripSyncPreview preview, List<RoundTripResolutionAction> actions,
        List<ElementConflict> unresolved, boolean canApply, Map<String, String> metadata) {
    public RoundTripResolutionPlan {
        operationId = requireText(operationId, "operationId");
        technology = Objects.requireNonNull(technology, "technology");
        profileId = requireText(profileId, "profileId");
        targetRoot = Objects.requireNonNull(targetRoot, "targetRoot").toAbsolutePath().normalize();
        preview = Objects.requireNonNull(preview, "preview");
        actions = List.copyOf(Objects.requireNonNull(actions, "actions"));
        unresolved = List.copyOf(Objects.requireNonNull(unresolved, "unresolved"));
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        if(canApply != unresolved.isEmpty()){throw new IllegalArgumentException("canApply must reflect unresolved conflicts");}
    }
    /** Descripción: Valida las condiciones necesarias para «requireText» y rechaza entradas no válidas. Entradas: value: valor de entrada que debe procesarse; field: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String requireText(String value, String field){
        Objects.requireNonNull(value, field);
        if(value.isBlank()){throw new IllegalArgumentException(field + " must not be blank");}
        return value.trim();
    }
}
