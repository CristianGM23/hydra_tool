package com.example.editor.contract;

import java.util.regex.Pattern;

/** Política neutral y compartida para identificadores canónicos estables. */
public final class StableIdentifierPolicy {
    private static final Pattern DOCUMENT_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,119}");
    private static final Pattern ELEMENT_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0,239}");

    /** Descripción: Inicializa la política neutral de identificadores. Entradas: no requiere parámetros. Salida: no crea instancias públicas. */
    /** Descripción: Inicializa una instancia de StableIdentifierPolicy con sus dependencias y datos de entrada. Entradas: no requiere parámetros de entrada. Salida: deja la instancia inicializada y lista para su uso. */ private StableIdentifierPolicy() {}

    /** Descripción: Comprueba si un valor es un identificador estable de documento. Entradas: value: valor a comprobar. Salida: true cuando cumple la política. */
    /** Descripción: Evalúa la condición «isValidDocumentId» sin modificar el estado externo. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve un indicador que expresa el resultado de la condición. */ public static boolean isValidDocumentId(String value) { return value != null && DOCUMENT_ID.matcher(value).matches(); }

    /** Descripción: Comprueba si un valor es un identificador estable de elemento. Entradas: value: valor a comprobar. Salida: true cuando cumple la política. */
    /** Descripción: Evalúa la condición «isValidElementId» sin modificar el estado externo. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve un indicador que expresa el resultado de la condición. */ public static boolean isValidElementId(String value) { return value != null && ELEMENT_ID.matcher(value).matches(); }

    /** Descripción: Exige un identificador estable de documento. Entradas: value y fieldName. Salida: devuelve el valor validado o lanza IllegalArgumentException. */
    /** Descripción: Valida las condiciones necesarias para «requireDocumentId» y rechaza entradas no válidas. Entradas: value: valor de entrada que debe procesarse; fieldName: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static String requireDocumentId(String value, String fieldName) {
        if(!isValidDocumentId(value)) throw new IllegalArgumentException(label(fieldName) + " must be a stable document id");
        return value;
    }

    /** Descripción: Exige un identificador estable de elemento. Entradas: value y fieldName. Salida: devuelve el valor validado o lanza IllegalArgumentException. */
    /** Descripción: Valida las condiciones necesarias para «requireElementId» y rechaza entradas no válidas. Entradas: value: valor de entrada que debe procesarse; fieldName: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static String requireElementId(String value, String fieldName) {
        if(!isValidElementId(value)) throw new IllegalArgumentException(label(fieldName) + " must be a stable element id");
        return value;
    }

    /** Descripción: Normaliza la etiqueta utilizada en los errores de validación. Entradas: fieldName. Salida: etiqueta no vacía. */
    /** Descripción: Ejecuta la operación «label» correspondiente a la responsabilidad de StableIdentifierPolicy. Entradas: fieldName: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String label(String fieldName) { return fieldName == null || fieldName.isBlank() ? "Identifier" : fieldName; }
}
