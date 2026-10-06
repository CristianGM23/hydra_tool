package com.example.editor.metamodel.layout;

import java.util.Locale;

import com.example.editor.metamodel.design.MetamodelDesignException;
import com.example.editor.metamodel.design.MetamodelDesignException.Reason;

/**
 * Define la dirección principal del layout.
 */
public enum MetamodelLayoutDirection {
    LEFT_TO_RIGHT,
    TOP_TO_BOTTOM;

    /**
     * Descripción:
     * Convierte una dirección textual.
     *
     * @param value contiene el valor recibido.
     * @return la dirección normalizada.
     */
    /** Descripción: Ejecuta la operación «fromValue» correspondiente a la responsabilidad de MetamodelLayoutDirection. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ public static MetamodelLayoutDirection fromValue(String value) {
        if(value == null || value.isBlank()) {
            return LEFT_TO_RIGHT;
        }

        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch(IllegalArgumentException exception) {
            throw new MetamodelDesignException(Reason.INVALID_REQUEST, "Unknown metamodel layout direction: " + value);
        }
    }
}
