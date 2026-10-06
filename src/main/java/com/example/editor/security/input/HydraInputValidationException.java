package com.example.editor.security.input;

/** Rechazo estable de una entrada que no satisface la política transversal. */
public class HydraInputValidationException extends RuntimeException {
    private final String field;

    /** Descripción: Inicializa una instancia de HydraInputValidationException con sus dependencias y datos de entrada. Entradas: field: dato de entrada necesario para la operación; message: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public HydraInputValidationException(String field, String message) {
        super(message);
        this.field = field == null ? "" : field;
    }

    /** Descripción: Ejecuta la operación «field» correspondiente a la responsabilidad de HydraInputValidationException. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public String field() {
        return field;
    }
}
