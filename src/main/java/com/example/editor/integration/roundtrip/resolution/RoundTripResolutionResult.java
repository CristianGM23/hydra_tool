package com.example.editor.integration.roundtrip.resolution;

import java.util.List;
import java.util.Objects;

/** Preview result. Application is allowed only when no blocking conflict remains. */
public record RoundTripResolutionResult(
        List<ResolvedElementConflict> resolved,
        List<ElementConflict> unresolved,
        boolean canApply) {

    public RoundTripResolutionResult {
        resolved = List.copyOf(Objects.requireNonNull(resolved, "resolved"));
        unresolved = List.copyOf(Objects.requireNonNull(unresolved, "unresolved"));
        if(canApply != unresolved.isEmpty()){
            throw new IllegalArgumentException("canApply must reflect whether unresolved conflicts are empty");
        }
    }
}
