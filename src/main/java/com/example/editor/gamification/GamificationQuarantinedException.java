package com.example.editor.gamification;

/**
 * The read found the gamification configuration malformed and moved it into quarantine. It is a NOT_FOUND (the configuration is no longer
 * in the active store), but unlike a plain absence the client must be told what happened, so the response carries its public message.
 */
public final class GamificationQuarantinedException extends GamificationApplicationException {
    /** Descripción: Inicializa una instancia de GamificationQuarantinedException. Entradas: message: mensaje público; cause: fallo de lectura que provocó la cuarentena. Salida: deja la instancia inicializada y lista para su uso. */ public GamificationQuarantinedException(String message, Throwable cause) {
        super(Reason.NOT_FOUND, message, cause);
    }
}
