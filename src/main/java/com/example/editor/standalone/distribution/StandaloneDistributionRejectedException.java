package com.example.editor.standalone.distribution;

/**
 * Rechazo deliberado del empaquetado de un editor standalone (validación con errores, secretos o enlaces simbólicos).
 * Extiende {@link IllegalStateException} para no cambiar el contrato del empaquetador; la capa de aplicación lo traduce a un error de cliente.
 */
public class StandaloneDistributionRejectedException extends IllegalStateException {
    private static final long serialVersionUID = 1L;

    /** Descripción: Crea el rechazo de empaquetado con un mensaje seguro para el cliente (solo rutas relativas, sin detalles internos). Entradas: message: motivo del rechazo. Salida: deja la excepción inicializada. */ public StandaloneDistributionRejectedException(String message) { super(message); }
}
