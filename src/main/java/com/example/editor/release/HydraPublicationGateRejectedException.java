package com.example.editor.release;

/**
 * Rechazo previsible de una publicación porque la puerta de release o la plataforma no están aptas.
 * Extiende IllegalStateException para no cambiar el contrato de quienes ya la capturan; el controlador de la
 * publicación verificada la traduce a un 409 sin tocar el manejador global.
 */
public class HydraPublicationGateRejectedException extends IllegalStateException {
    /** Descripción: Inicializa una instancia de HydraPublicationGateRejectedException con el motivo del rechazo. Entradas: message: motivo por el que la puerta no permite publicar. Salida: deja la instancia inicializada y lista para su uso. */ public HydraPublicationGateRejectedException(String message) { super(message); }
}
