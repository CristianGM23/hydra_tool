package com.example.editor.integration.roundtrip.resolution;

import java.util.Objects;

/** Decision made for a specific conflict key. */
public record ElementConflictDecision(
        String conflictKey,
        ConflictResolutionChoice choice,
        String rationale) {

    public ElementConflictDecision {
        conflictKey = requireText(conflictKey, "conflictKey");
        choice = Objects.requireNonNull(choice, "choice");
        rationale = requireText(rationale, "rationale");
    }

    /** Descripción: Valida las condiciones necesarias para «requireText» y rechaza entradas no válidas. Entradas: value: valor de entrada que debe procesarse; field: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String requireText(String value, String field){
        Objects.requireNonNull(value, field);
        if(value.isBlank()){
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
