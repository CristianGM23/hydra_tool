package com.example.editor.integration.web;

import com.example.editor.integration.roundtrip.resolution.RoundTripResolutionAction;
import com.example.editor.integration.roundtrip.resolution.RoundTripResolutionActionKind;

/** Serializable action view that deliberately omits generated file bytes. */
public record RoundTripResolutionActionView(String path, RoundTripResolutionActionKind kind,
        String mediaType, String conflictKey) {
    /** Descripción: Ejecuta la operación «from» correspondiente a la responsabilidad de RoundTripResolutionActionView. Entradas: action: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ static RoundTripResolutionActionView from(RoundTripResolutionAction action){
        return new RoundTripResolutionActionView(action.path(), action.kind(), action.mediaType(), action.conflictKey());
    }
}
